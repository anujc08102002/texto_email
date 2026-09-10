package com.texto.emailplatform.apikey;

import com.texto.emailplatform.apikey.api.ApiKeyResponse;
import com.texto.emailplatform.apikey.api.CreateApiKeyRequest;
import com.texto.emailplatform.apikey.api.CreatedApiKeyResponse;
import com.texto.emailplatform.auth.domain.ApiKeyEntity;
import com.texto.emailplatform.auth.domain.ApiKeyRepository;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.auth.security.HashedApiKeyAuthenticator;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.PermissionAuthorizationManager;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.UsageService;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

    static final String ENVIRONMENT_TEST = "TEST";
    static final String ENVIRONMENT_LIVE = "LIVE";
    private static final int PREFIX_LENGTH = 12;

    private final ApiKeyRepository apiKeyRepository;
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final PermissionAuthorizationManager permissionAuthorizationManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            EntitlementService entitlementService,
            UsageService usageService,
            PermissionAuthorizationManager permissionAuthorizationManager
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.permissionAuthorizationManager = permissionAuthorizationManager;
    }

    @Transactional
    public CreatedApiKeyResponse create(DashboardPrincipal principal, CreateApiKeyRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.API_KEY_CREATE);
        UUID tenantId = requireTenantId();
        String environment = normalizeEnvironment(request.environment());

        long activeKeys = apiKeyRepository.countByTenantIdAndStatus(tenantId, ApiKeyEntity.STATUS_ACTIVE);
        OptionalLong limit = entitlementService.getLimit(tenantId, UsageMetrics.API_KEYS);
        if (limit.isPresent() && activeKeys + 1 > limit.getAsLong()) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "QUOTA_EXCEEDED",
                    "This plan allows at most " + limit.getAsLong() + " active API keys"
            );
        }

        String secret = generateSecret(environment);
        ApiKeyEntity entity = apiKeyRepository.save(ApiKeyEntity.create(
                tenantId,
                request.name().trim(),
                secret.substring(0, PREFIX_LENGTH),
                HashedApiKeyAuthenticator.sha256(secret),
                environment,
                request.expiresAt()
        ));
        usageService.recordUsage(tenantId, UsageMetrics.API_KEYS, activeKeys + 1);
        return new CreatedApiKeyResponse(toResponse(entity), secret);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(DashboardPrincipal principal) {
        permissionAuthorizationManager.requirePermission(principal, Permission.API_KEY_READ);
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(requireTenantId()).stream()
                .map(ApiKeyService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse get(DashboardPrincipal principal, UUID apiKeyId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.API_KEY_READ);
        return toResponse(requireOwnedKey(apiKeyId));
    }

    @Transactional
    public ApiKeyResponse revoke(DashboardPrincipal principal, UUID apiKeyId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.API_KEY_REVOKE);
        UUID tenantId = requireTenantId();
        ApiKeyEntity entity = requireOwnedKey(apiKeyId);
        if (ApiKeyEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            entity.revoke();
            apiKeyRepository.save(entity);
        }
        usageService.recordUsage(
                tenantId,
                UsageMetrics.API_KEYS,
                apiKeyRepository.countByTenantIdAndStatus(tenantId, ApiKeyEntity.STATUS_ACTIVE)
        );
        return toResponse(entity);
    }

    private ApiKeyEntity requireOwnedKey(UUID apiKeyId) {
        return apiKeyRepository.findByIdAndTenantId(apiKeyId, requireTenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "API_KEY_NOT_FOUND",
                        "API key was not found"
                ));
    }

    private static UUID requireTenantId() {
        return TenantContext.get()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHENTICATED",
                        "Authentication is required"
                ));
    }

    private static String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return ENVIRONMENT_TEST;
        }
        String normalized = environment.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENT_TEST.equals(normalized) && !ENVIRONMENT_LIVE.equals(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "VALIDATION_ERROR",
                    "environment must be TEST or LIVE"
            );
        }
        return normalized;
    }

    private String generateSecret(String environment) {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String scope = ENVIRONMENT_LIVE.equals(environment) ? "live" : "test";
        return "sk_" + scope + "_" + HexFormat.of().formatHex(bytes);
    }

    private static ApiKeyResponse toResponse(ApiKeyEntity entity) {
        return new ApiKeyResponse(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getStatus(),
                entity.getEnvironment(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }
}
