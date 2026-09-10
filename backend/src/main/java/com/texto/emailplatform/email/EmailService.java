package com.texto.emailplatform.email;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.RolePermissions;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.email.api.DeliveryAttemptResponse;
import com.texto.emailplatform.email.api.EmailMessageDetailResponse;
import com.texto.emailplatform.email.api.EmailMessagePageResponse;
import com.texto.emailplatform.email.api.EmailMessageResponse;
import com.texto.emailplatform.email.api.SendEmailRequest;
import com.texto.emailplatform.email.api.SendTestEmailRequest;
import com.texto.emailplatform.email.domain.DeliveryAttemptEntity;
import com.texto.emailplatform.email.domain.DeliveryAttemptRepository;
import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.email.domain.EmailMessageRepository;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.outbox.OutboxEventEntity;
import com.texto.emailplatform.outbox.OutboxService;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.suppression.EmailNormalizer;
import com.texto.emailplatform.suppression.SuppressionService;
import com.texto.emailplatform.template.TemplateRenderer;
import com.texto.emailplatform.template.TemplateService;
import com.texto.emailplatform.template.domain.TemplateVersionEntity;
import com.texto.emailplatform.tenant.domain.TenantEntity;
import com.texto.emailplatform.tenant.domain.TenantRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.UsageService;
import com.texto.emailplatform.webhook.WebhookEventPublisher;
import com.texto.emailplatform.webhook.WebhookEventTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async email acceptance API.
 *
 * <p><b>Billing:</b> ONE RECIPIENT = ONE EMAIL UNIT. Quota is consumed for deliverable
 * recipients only (suppressed addresses are excluded and do not consume quota).
 */
@Service
public class EmailService {

    private final EmailMessageRepository emailMessageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final TenantRepository tenantRepository;
    private final DomainService domainService;
    private final SuppressionService suppressionService;
    private final TemplateService templateService;
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final WebhookEventPublisher webhookEventPublisher;
    private final OutboxService outboxService;
    private final EmailPlatformProperties properties;

    public EmailService(
            EmailMessageRepository emailMessageRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            TenantRepository tenantRepository,
            DomainService domainService,
            SuppressionService suppressionService,
            TemplateService templateService,
            EntitlementService entitlementService,
            UsageService usageService,
            WebhookEventPublisher webhookEventPublisher,
            OutboxService outboxService,
            EmailPlatformProperties properties
    ) {
        this.emailMessageRepository = emailMessageRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.tenantRepository = tenantRepository;
        this.domainService = domainService;
        this.suppressionService = suppressionService;
        this.templateService = templateService;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.webhookEventPublisher = webhookEventPublisher;
        this.outboxService = outboxService;
        this.properties = properties;
    }

    @Transactional
    public EmailMessageResponse sendTest(SendTestEmailRequest request) {
        UUID tenantId = requireTenantId();
        TenantEntity tenant = requireTenant(tenantId);
        String from = "noreply@" + tenant.getSlug() + ".texto.test";
        return sendInternal(
                tenantId,
                from,
                List.of(request.to().trim()),
                List.of(),
                List.of(),
                null,
                request.subject().trim(),
                null,
                request.body(),
                null,
                null,
                null,
                null,
                null
        );
    }

    @Transactional
    public EmailMessageResponse send(SendEmailRequest request, String idempotencyKey) {
        UUID tenantId = requireTenantId();
        return sendInternal(
                tenantId,
                request.from().trim(),
                request.to(),
                request.cc(),
                request.bcc(),
                request.replyTo(),
                request.subject(),
                request.html(),
                request.text(),
                request.templateId(),
                request.templateVersion(),
                request.variables(),
                request.metadata(),
                idempotencyKey
        );
    }

    private EmailMessageResponse sendInternal(
            UUID tenantId,
            String from,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String replyTo,
            String subject,
            String html,
            String text,
            UUID templateId,
            Integer templateVersion,
            Map<String, Object> variables,
            Map<String, Object> metadata,
            String idempotencyKey
    ) {
        authorizeSend(tenantId);

        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            var existing = emailMessageRepository.findByTenantIdAndIdempotencyKey(tenantId, normalizedKey);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        String resolvedSubject = subject;
        String resolvedHtml = html;
        String resolvedText = text;
        UUID resolvedTemplateId = null;
        UUID resolvedTemplateVersionId = null;

        if (templateId != null) {
            if (!entitlementService.canUseFeature(tenantId, FeatureCodes.TEMPLATES)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN.value(),
                        "FEATURE_NOT_AVAILABLE",
                        "Templates are not available on the current plan"
                );
            }
            TemplateVersionEntity version = templateService.requireRenderableVersion(tenantId, templateId, templateVersion);
            TemplateRenderer.RenderedTemplate rendered = templateService.render(version, variables);
            resolvedSubject = rendered.subject();
            resolvedHtml = rendered.htmlContent();
            resolvedText = rendered.textContent();
            resolvedTemplateId = templateId;
            resolvedTemplateVersionId = version.getId();
        }

        if (resolvedSubject == null || resolvedSubject.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "subject is required");
        }
        if ((resolvedHtml == null || resolvedHtml.isBlank()) && (resolvedText == null || resolvedText.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "html or text body is required");
        }

        domainService.requireVerifiedSender(tenantId, from);

        RecipientBuckets buckets = splitRecipients(tenantId, to, cc, bcc);
        if (buckets.totalCount() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "at least one recipient is required");
        }
        if (buckets.totalCount() > properties.getEmail().getMaxRecipients()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "TOO_MANY_RECIPIENTS",
                    "Maximum recipients per message is " + properties.getEmail().getMaxRecipients()
            );
        }

        String normalizedReplyTo = replyTo == null || replyTo.isBlank() ? null : replyTo.trim();
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        UUID createdBy = SecuritySupport.dashboardPrincipal(SecurityContextHolder.getContext().getAuthentication())
                .map(DashboardPrincipal::userId)
                .orElse(null);

        if (buckets.deliverableCount() == 0) {
            EmailMessageEntity suppressed = EmailMessageEntity.create(
                    tenantId,
                    from,
                    buckets.to(),
                    buckets.cc(),
                    buckets.bcc(),
                    buckets.suppressed(),
                    normalizedReplyTo,
                    resolvedSubject.trim(),
                    resolvedHtml,
                    resolvedText,
                    resolvedTemplateId,
                    resolvedTemplateVersionId,
                    normalizedKey,
                    safeMetadata,
                    properties.getEmail().getMaxAttempts(),
                    createdBy
            );
            suppressed.markSuppressed("suppressed_recipient");
            try {
                suppressed = emailMessageRepository.save(suppressed);
            } catch (DataIntegrityViolationException exception) {
                return refetchIdempotent(tenantId, normalizedKey, exception);
            }
            webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_SUPPRESSED, suppressed);
            return toResponse(suppressed);
        }

        usageService.consumeQuota(tenantId, UsageMetrics.MONTHLY_EMAILS, buckets.deliverableCount());

        EmailMessageEntity message = EmailMessageEntity.create(
                tenantId,
                from,
                buckets.to(),
                buckets.cc(),
                buckets.bcc(),
                buckets.suppressed(),
                normalizedReplyTo,
                resolvedSubject.trim(),
                resolvedHtml,
                resolvedText,
                resolvedTemplateId,
                resolvedTemplateVersionId,
                normalizedKey,
                safeMetadata,
                properties.getEmail().getMaxAttempts(),
                createdBy
        );
        try {
            message = emailMessageRepository.save(message);
        } catch (DataIntegrityViolationException exception) {
            return refetchIdempotent(tenantId, normalizedKey, exception);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", message.getId().toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("attempt", 1);
        outboxService.enqueue(
                tenantId,
                OutboxEventEntity.AGGREGATE_EMAIL_MESSAGE,
                message.getId(),
                OutboxEventEntity.EMAIL_DELIVERY_REQUESTED,
                payload
        );
        webhookEventPublisher.publishEmailEvent(WebhookEventTypes.EMAIL_QUEUED, message);
        return toResponse(message);
    }

    @Transactional(readOnly = true)
    public EmailMessagePageResponse list(int page, int size, String status, String q) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        UUID tenantId = requireTenantId();
        String statusFilter = blankToNull(status);
        String query = blankToNull(q);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        // Avoid binding a null search term into LOWER/CONCAT — PostgreSQL types it as bytea and fails.
        Page<EmailMessageEntity> result;
        if (query == null) {
            result = statusFilter == null
                    ? emailMessageRepository.findByTenantId(tenantId, pageable)
                    : emailMessageRepository.findByTenantIdAndStatus(tenantId, statusFilter, pageable);
        } else {
            result = emailMessageRepository.searchByTenantId(tenantId, statusFilter, query, pageable);
        }
        return new EmailMessagePageResponse(
                result.getContent().stream().map(EmailService::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<EmailMessageResponse> listRecent() {
        return emailMessageRepository.findTop50ByTenantIdOrderByCreatedAtDesc(requireTenantId()).stream()
                .map(EmailService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailMessageDetailResponse get(UUID messageId) {
        EmailMessageEntity entity = emailMessageRepository.findByIdAndTenantId(messageId, requireTenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "EMAIL_NOT_FOUND",
                        "Email message was not found"
                ));
        List<DeliveryAttemptEntity> attempts = deliveryAttemptRepository
                .findByMessageIdAndTenantIdOrderByAttemptNumberAsc(entity.getId(), entity.getTenantId());
        return toDetailResponse(entity, attempts);
    }

    private EmailMessageResponse refetchIdempotent(UUID tenantId, String key, DataIntegrityViolationException exception) {
        if (key == null) {
            throw exception;
        }
        return emailMessageRepository.findByTenantIdAndIdempotencyKey(tenantId, key)
                .map(EmailService::toResponse)
                .orElseThrow(() -> exception);
    }

    private RecipientBuckets splitRecipients(UUID tenantId, List<String> to, List<String> cc, List<String> bcc) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> deliverableTo = new ArrayList<>();
        List<String> deliverableCc = new ArrayList<>();
        List<String> deliverableBcc = new ArrayList<>();
        List<String> suppressed = new ArrayList<>();

        for (String raw : nullSafe(to)) {
            classify(tenantId, raw, seen, deliverableTo, suppressed);
        }
        for (String raw : nullSafe(cc)) {
            classify(tenantId, raw, seen, deliverableCc, suppressed);
        }
        for (String raw : nullSafe(bcc)) {
            classify(tenantId, raw, seen, deliverableBcc, suppressed);
        }
        return new RecipientBuckets(deliverableTo, deliverableCc, deliverableBcc, suppressed);
    }

    private void classify(
            UUID tenantId,
            String raw,
            LinkedHashSet<String> seen,
            List<String> deliverable,
            List<String> suppressed
    ) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String email = raw.trim();
        String normalized = EmailNormalizer.normalize(email);
        if (!seen.add(normalized)) {
            return;
        }
        if (suppressionService.isSuppressed(tenantId, email)) {
            suppressed.add(email);
        } else {
            deliverable.add(email);
        }
    }

    private static List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void authorizeSend(UUID tenantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var dashboard = SecuritySupport.dashboardPrincipal(authentication);
        if (dashboard.isPresent()) {
            DashboardPrincipal principal = dashboard.get();
            if (!RolePermissions.has(principal.role(), Permission.EMAIL_SEND)) {
                throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access is denied");
            }
        }
        if (entitlementService.hasFeature(tenantId, FeatureCodes.API_SENDING)) {
            if (!entitlementService.canUseFeature(tenantId, FeatureCodes.API_SENDING)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN.value(),
                        "FEATURE_NOT_AVAILABLE",
                        "API sending is not available for the current subscription"
                );
            }
        }
    }

    private TenantEntity requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHENTICATED",
                        "Authentication is required"
                ));
    }

    private UUID requireTenantId() {
        return TenantContext.get()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHENTICATED",
                        "Authentication is required"
                ));
    }

    private static EmailMessageResponse toResponse(EmailMessageEntity entity) {
        return new EmailMessageResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getRecipient(),
                entity.getFromAddress(),
                entity.getReplyTo(),
                entity.getSubject(),
                entity.getStatusReason(),
                entity.getRecipientsTo(),
                entity.getRecipientsCc(),
                entity.getRecipientsBcc(),
                entity.getSuppressedRecipients(),
                entity.getMetadata(),
                entity.getProviderMessageId(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getNextAttemptAt(),
                entity.getQueuedAt(),
                entity.getDeliveredAt(),
                entity.getFailedAt(),
                entity.getLastError(),
                entity.getTemplateId(),
                entity.getTemplateVersionId(),
                entity.getCreatedAt()
        );
    }

    private static EmailMessageDetailResponse toDetailResponse(
            EmailMessageEntity entity,
            List<DeliveryAttemptEntity> attempts
    ) {
        return new EmailMessageDetailResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getRecipient(),
                entity.getFromAddress(),
                entity.getReplyTo(),
                entity.getSubject(),
                entity.getStatusReason(),
                entity.getRecipientsTo(),
                entity.getRecipientsCc(),
                entity.getRecipientsBcc(),
                entity.getSuppressedRecipients(),
                entity.getMetadata(),
                entity.getProviderMessageId(),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getNextAttemptAt(),
                entity.getQueuedAt(),
                entity.getProcessingAt(),
                entity.getSendingAt(),
                entity.getDeliveredAt(),
                entity.getFailedAt(),
                entity.getLastError(),
                entity.getTemplateId(),
                entity.getTemplateVersionId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                attempts.stream().map(EmailService::toAttemptResponse).toList()
        );
    }

    private static DeliveryAttemptResponse toAttemptResponse(DeliveryAttemptEntity attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getProviderResponse(),
                attempt.getErrorCategory(),
                attempt.getErrorMessage()
        );
    }

    private record RecipientBuckets(
            List<String> to,
            List<String> cc,
            List<String> bcc,
            List<String> suppressed
    ) {
        int deliverableCount() {
            return to.size() + cc.size() + bcc.size();
        }

        int totalCount() {
            return deliverableCount() + suppressed.size();
        }
    }
}
