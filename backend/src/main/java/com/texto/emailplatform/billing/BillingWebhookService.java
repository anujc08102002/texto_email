package com.texto.emailplatform.billing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.billing.domain.BillingEventEntity;
import com.texto.emailplatform.billing.domain.BillingEventRepository;
import com.texto.emailplatform.billing.spi.ProviderWebhookEvent;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.subscription.SubscriptionStatus;
import com.texto.emailplatform.subscription.domain.SubscriptionEntity;
import com.texto.emailplatform.subscription.domain.SubscriptionRepository;
import com.texto.emailplatform.usage.UsageService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingWebhookService {

    private static final Logger log = LoggerFactory.getLogger(BillingWebhookService.class);

    private final BillingProvider billingProvider;
    private final BillingEventRepository billingEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageService usageService;
    private final EmailPlatformProperties properties;
    private final ObjectMapper objectMapper;

    public BillingWebhookService(
            BillingProvider billingProvider,
            BillingEventRepository billingEventRepository,
            SubscriptionRepository subscriptionRepository,
            UsageService usageService,
            EmailPlatformProperties properties,
            ObjectMapper objectMapper
    ) {
        this.billingProvider = billingProvider;
        this.billingEventRepository = billingEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageService = usageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleRazorpayWebhook(String rawBody, String signatureHeader) {
        if (!billingProvider.verifyWebhook(rawBody, signatureHeader)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED.value(),
                    "BILLING_WEBHOOK_INVALID_SIGNATURE",
                    "Invalid billing webhook signature"
            );
        }
        ProviderWebhookEvent event = billingProvider.parseWebhookEvent(rawBody);
        processEvent(event);
    }

    @Transactional
    public void processEvent(ProviderWebhookEvent event) {
        if (event == null || event.providerEventId() == null || event.providerEventId().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "BILLING_WEBHOOK_INVALID",
                    "Webhook event id is required"
            );
        }
        if (billingEventRepository.existsByProviderAndProviderEventId(
                billingProvider.name(),
                event.providerEventId()
        )) {
            log.info("Ignoring duplicate billing event {}", event.providerEventId());
            return;
        }

        BillingEventEntity billingEvent = BillingEventEntity.received(
                billingProvider.name(),
                event.providerEventId(),
                event.type() == null ? "unknown" : event.type(),
                toPayloadMap(event)
        );

        try {
            billingEventRepository.saveAndFlush(billingEvent);
        } catch (DataIntegrityViolationException exception) {
            log.info("Ignoring duplicate billing event {}", event.providerEventId());
            return;
        }

        try {
            Optional<SubscriptionEntity> subscription = findSubscription(event);
            if (subscription.isEmpty()) {
                billingEvent.markIgnored("No matching subscription for provider event");
                billingEventRepository.save(billingEvent);
                return;
            }

            SubscriptionEntity entity = subscription.get();
            billingEvent.link(entity.getTenantId(), entity.getId());
            applyEvent(entity, event);
            subscriptionRepository.save(entity);
            billingEvent.markProcessed();
            billingEventRepository.save(billingEvent);
        } catch (RuntimeException exception) {
            billingEvent.markFailed(exception.getMessage());
            billingEventRepository.save(billingEvent);
            throw exception;
        }
    }

    private Optional<SubscriptionEntity> findSubscription(ProviderWebhookEvent event) {
        if (event.providerSubscriptionId() == null || event.providerSubscriptionId().isBlank()) {
            return Optional.empty();
        }
        return subscriptionRepository.findByProviderAndProviderSubscriptionId(
                billingProvider.name(),
                event.providerSubscriptionId()
        );
    }

    private void applyEvent(SubscriptionEntity subscription, ProviderWebhookEvent event) {
        if (event.providerStatus() != null) {
            subscription.updateProviderStatus(event.providerStatus());
        }
        if (event.periodStart() != null || event.periodEnd() != null) {
            subscription.applyPeriod(event.periodStart(), event.periodEnd());
        }

        if (event.paymentFailure()) {
            applyPaymentFailure(subscription);
            return;
        }

        String status = event.suggestedInternalStatus();

        if (event.activation() || SubscriptionStatus.ACTIVE.equals(status)) {
            activateSubscription(subscription);
            return;
        }

        if (status == null) {
            return;
        }

        if (SubscriptionStatus.CANCELLED.equals(status)) {
            boolean stillInPeriod = subscription.getCurrentPeriodEnd() != null
                    && subscription.getCurrentPeriodEnd().isAfter(Instant.now());
            if (stillInPeriod) {
                subscription.cancel(true);
            } else {
                subscription.cancel(false);
            }
            return;
        }

        if (SubscriptionStatus.PAST_DUE.equals(status)) {
            applyPaymentFailure(subscription);
            return;
        }

        subscription.changeStatus(status);
        if (SubscriptionStatus.ACTIVE.equals(status)) {
            subscription.clearGrace();
        }
    }

    private void activateSubscription(SubscriptionEntity subscription) {
        if (subscription.getPendingPlanId() != null) {
            subscription.activatePendingPlan(subscription.getPendingPlanId());
        } else {
            subscription.changeStatus(SubscriptionStatus.ACTIVE);
            subscription.clearGrace();
        }
        usageService.ensurePeriodRows(subscription.getTenantId());
    }

    private void applyPaymentFailure(SubscriptionEntity subscription) {
        int graceDays = properties.getBilling().getGracePeriodDays();
        Instant now = Instant.now();
        if (graceDays > 0) {
            if (subscription.getGracePeriodEndsAt() == null) {
                subscription.markPastDue();
                subscription.setGracePeriodEndsAt(now.plus(graceDays, ChronoUnit.DAYS));
            } else if (now.isAfter(subscription.getGracePeriodEndsAt())) {
                subscription.markGrace(subscription.getGracePeriodEndsAt());
            } else {
                subscription.markPastDue();
            }
        } else {
            subscription.markPastDue();
        }
    }

    private Map<String, Object> toPayloadMap(ProviderWebhookEvent event) {
        if (event.rawPayload() == null || event.rawPayload().isNull()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(event.rawPayload(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
