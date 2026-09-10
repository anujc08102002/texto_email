package com.texto.emailplatform.webhook;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.PermissionAuthorizationManager;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.webhook.api.CreateWebhookRequest;
import com.texto.emailplatform.webhook.api.CreatedWebhookResponse;
import com.texto.emailplatform.webhook.api.PatchWebhookRequest;
import com.texto.emailplatform.webhook.api.WebhookConfigResponse;
import com.texto.emailplatform.webhook.api.WebhookEventResponse;
import com.texto.emailplatform.webhook.domain.WebhookConfigEntity;
import com.texto.emailplatform.webhook.domain.WebhookConfigRepository;
import com.texto.emailplatform.webhook.domain.WebhookEventEntity;
import com.texto.emailplatform.webhook.domain.WebhookEventRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private static final int PREFIX_LENGTH = 12;

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookUrlValidator webhookUrlValidator;
    private final WebhookEventPublisher webhookEventPublisher;
    private final WebhookSecretProtector webhookSecretProtector;
    private final EntitlementService entitlementService;
    private final PermissionAuthorizationManager permissionAuthorizationManager;

    public WebhookService(
            WebhookConfigRepository webhookConfigRepository,
            WebhookEventRepository webhookEventRepository,
            WebhookUrlValidator webhookUrlValidator,
            WebhookEventPublisher webhookEventPublisher,
            WebhookSecretProtector webhookSecretProtector,
            EntitlementService entitlementService,
            PermissionAuthorizationManager permissionAuthorizationManager
    ) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookUrlValidator = webhookUrlValidator;
        this.webhookEventPublisher = webhookEventPublisher;
        this.webhookSecretProtector = webhookSecretProtector;
        this.entitlementService = entitlementService;
        this.permissionAuthorizationManager = permissionAuthorizationManager;
    }

    @Transactional
    public CreatedWebhookResponse create(DashboardPrincipal principal, CreateWebhookRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        UUID tenantId = requireTenantId();
        requireFeature(tenantId);
        String url = webhookUrlValidator.validate(request.url());
        String secret = WebhookSecretHasher.generateSecret();
        // Hash retained for audit/compare; protected material used for outbound signing.
                WebhookSecretHasher.sha256(secret);
        WebhookConfigEntity entity = webhookConfigRepository.save(WebhookConfigEntity.create(
                tenantId,
                url,
                blankToNull(request.description()),
                secret.substring(0, PREFIX_LENGTH),
                webhookSecretProtector.protect(secret),
                request.eventTypes()
        ));
        return new CreatedWebhookResponse(toResponse(entity), secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookConfigResponse> list(DashboardPrincipal principal) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_READ);
        return webhookConfigRepository.findByTenantIdOrderByCreatedAtDesc(requireTenantId()).stream()
                .map(WebhookService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookConfigResponse get(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_READ);
        return toResponse(requireOwned(webhookId));
    }

    @Transactional
    public WebhookConfigResponse patch(DashboardPrincipal principal, UUID webhookId, PatchWebhookRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        requireFeature(requireTenantId());
        WebhookConfigEntity entity = requireOwned(webhookId);
        String url = request.url() == null ? null : webhookUrlValidator.validate(request.url());
        entity.patch(url, request.description(), request.eventTypes());
        return toResponse(webhookConfigRepository.save(entity));
    }

    @Transactional
    public WebhookConfigResponse pause(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        WebhookConfigEntity entity = requireOwned(webhookId);
        entity.pause();
        return toResponse(webhookConfigRepository.save(entity));
    }

    @Transactional
    public WebhookConfigResponse resume(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        requireFeature(requireTenantId());
        WebhookConfigEntity entity = requireOwned(webhookId);
        entity.resume();
        return toResponse(webhookConfigRepository.save(entity));
    }

    @Transactional
    public CreatedWebhookResponse rotateSecret(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        requireFeature(requireTenantId());
        WebhookConfigEntity entity = requireOwned(webhookId);
        String secret = WebhookSecretHasher.generateSecret();
        WebhookSecretHasher.sha256(secret);
        entity.rotateSecret(secret.substring(0, PREFIX_LENGTH), webhookSecretProtector.protect(secret));
        return new CreatedWebhookResponse(toResponse(webhookConfigRepository.save(entity)), secret);
    }

    @Transactional
    public void delete(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        WebhookConfigEntity entity = requireOwned(webhookId);
        entity.disable();
        webhookConfigRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> listEvents(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_READ);
        requireOwned(webhookId);
        return webhookEventRepository
                .findByWebhookConfigIdAndTenantIdOrderByCreatedAtDesc(webhookId, requireTenantId())
                .stream()
                .map(WebhookService::toEventResponse)
                .toList();
    }

    @Transactional
    public WebhookEventResponse sendTest(DashboardPrincipal principal, UUID webhookId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.WEBHOOK_MANAGE);
        requireFeature(requireTenantId());
        WebhookConfigEntity config = requireOwned(webhookId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("message", "Texto webhook test event");
        WebhookEventEntity event = webhookEventPublisher.publish(config, WebhookEventTypes.WEBHOOK_TEST, data, null);
        return toEventResponse(event);
    }

    private WebhookConfigEntity requireOwned(UUID webhookId) {
        return webhookConfigRepository.findByIdAndTenantId(webhookId, requireTenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "WEBHOOK_NOT_FOUND",
                        "Webhook was not found"
                ));
    }

    private void requireFeature(UUID tenantId) {
        if (!entitlementService.canUseFeature(tenantId, FeatureCodes.WEBHOOKS)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "FEATURE_NOT_AVAILABLE",
                    "Webhooks are not available on the current plan"
            );
        }
    }

    private static WebhookConfigResponse toResponse(WebhookConfigEntity entity) {
        return new WebhookConfigResponse(
                entity.getId(),
                entity.getUrl(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getSecretPrefix(),
                entity.getEventTypes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static WebhookEventResponse toEventResponse(WebhookEventEntity entity) {
        return new WebhookEventResponse(
                entity.getId(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getAttemptCount(),
                entity.getNextAttemptAt(),
                entity.getLastAttemptAt(),
                entity.getDeliveredAt(),
                entity.getLastResponseCode(),
                entity.getLastError(),
                entity.getSourceMessageId(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }

    private static UUID requireTenantId() {
        return TenantContext.get()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHENTICATED",
                        "Authentication is required"
                ));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
