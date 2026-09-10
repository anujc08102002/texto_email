package com.texto.emailplatform.suppression;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.PermissionAuthorizationManager;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.suppression.api.CreateSuppressionRequest;
import com.texto.emailplatform.suppression.api.ImportSuppressionsRequest;
import com.texto.emailplatform.suppression.api.ImportSuppressionsResponse;
import com.texto.emailplatform.suppression.api.SuppressionResponse;
import com.texto.emailplatform.suppression.domain.SuppressionEntity;
import com.texto.emailplatform.suppression.domain.SuppressionRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuppressionService {

    private final SuppressionRepository suppressionRepository;
    private final EntitlementService entitlementService;
    private final PermissionAuthorizationManager permissionAuthorizationManager;

    public SuppressionService(
            SuppressionRepository suppressionRepository,
            EntitlementService entitlementService,
            PermissionAuthorizationManager permissionAuthorizationManager
    ) {
        this.suppressionRepository = suppressionRepository;
        this.entitlementService = entitlementService;
        this.permissionAuthorizationManager = permissionAuthorizationManager;
    }

    @Transactional
    public SuppressionResponse createManual(DashboardPrincipal principal, CreateSuppressionRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.SUPPRESSION_MANAGE);
        UUID tenantId = requireTenantId();
        requireFeature(tenantId);
        return toResponse(upsert(
                tenantId,
                request.email(),
                request.reason() == null || request.reason().isBlank() ? "manual" : request.reason().trim(),
                SuppressionEntity.TYPE_MANUAL,
                SuppressionEntity.SOURCE_USER,
                null
        ));
    }

    @Transactional(readOnly = true)
    public List<SuppressionResponse> list(DashboardPrincipal principal, String search, String type) {
        permissionAuthorizationManager.requirePermission(principal, Permission.SUPPRESSION_READ);
        UUID tenantId = requireTenantId();
        String normalizedSearch = search == null || search.isBlank() ? null : EmailNormalizer.normalize(search);
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        List<SuppressionEntity> rows;
        if (normalizedSearch == null) {
            rows = normalizedType == null
                    ? suppressionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                    : suppressionRepository.findByTenantIdAndTypeOrderByCreatedAtDesc(tenantId, normalizedType);
        } else {
            rows = suppressionRepository.search(tenantId, normalizedType, normalizedSearch);
        }
        return rows.stream()
                .map(SuppressionService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SuppressionResponse get(DashboardPrincipal principal, UUID suppressionId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.SUPPRESSION_READ);
        return toResponse(requireOwned(suppressionId));
    }

    @Transactional
    public void delete(DashboardPrincipal principal, UUID suppressionId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.SUPPRESSION_MANAGE);
        requireFeature(requireTenantId());
        SuppressionEntity entity = requireOwned(suppressionId);
        suppressionRepository.delete(entity);
    }

    @Transactional
    public ImportSuppressionsResponse importEmails(DashboardPrincipal principal, ImportSuppressionsRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.SUPPRESSION_MANAGE);
        UUID tenantId = requireTenantId();
        requireFeature(tenantId);
        int imported = 0;
        int skipped = 0;
        for (String email : request.emails()) {
            if (email == null || email.isBlank() || !email.contains("@")) {
                skipped++;
                continue;
            }
            String normalized = EmailNormalizer.normalize(email);
            if (suppressionRepository.findByTenantIdAndNormalizedEmail(tenantId, normalized).isPresent()) {
                skipped++;
                continue;
            }
            upsert(tenantId, email.trim(), "import", SuppressionEntity.TYPE_MANUAL, SuppressionEntity.SOURCE_IMPORT, null);
            imported++;
        }
        return new ImportSuppressionsResponse(imported, skipped);
    }

    @Transactional(readOnly = true)
    public boolean isSuppressed(UUID tenantId, String email) {
        return suppressionRepository.findByTenantIdAndNormalizedEmail(tenantId, EmailNormalizer.normalize(email))
                .isPresent();
    }

    @Transactional
    public void recordBounce(UUID tenantId, String email, UUID messageId, String reason) {
        upsert(
                tenantId,
                email,
                reason == null || reason.isBlank() ? "bounce" : reason,
                SuppressionEntity.TYPE_BOUNCE,
                SuppressionEntity.SOURCE_SYSTEM,
                messageId
        );
    }

    @Transactional
    public void recordComplaint(UUID tenantId, String email, UUID messageId, String reason) {
        upsert(
                tenantId,
                email,
                reason == null || reason.isBlank() ? "complaint" : reason,
                SuppressionEntity.TYPE_COMPLAINT,
                SuppressionEntity.SOURCE_SYSTEM,
                messageId
        );
    }

    private SuppressionEntity upsert(
            UUID tenantId,
            String email,
            String reason,
            String type,
            String source,
            UUID messageId
    ) {
        String normalized = EmailNormalizer.normalize(email);
        return suppressionRepository.findByTenantIdAndNormalizedEmail(tenantId, normalized)
                .orElseGet(() -> suppressionRepository.save(SuppressionEntity.create(
                        tenantId,
                        email.trim(),
                        normalized,
                        reason,
                        type,
                        source,
                        messageId
                )));
    }

    private SuppressionEntity requireOwned(UUID suppressionId) {
        return suppressionRepository.findByIdAndTenantId(suppressionId, requireTenantId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "SUPPRESSION_NOT_FOUND",
                        "Suppression was not found"
                ));
    }

    private void requireFeature(UUID tenantId) {
        if (!entitlementService.canUseFeature(tenantId, FeatureCodes.SUPPRESSION)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "FEATURE_NOT_AVAILABLE",
                    "Suppressions are not available on the current plan"
            );
        }
    }

    private static SuppressionResponse toResponse(SuppressionEntity entity) {
        return new SuppressionResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getType(),
                entity.getReason(),
                entity.getSource(),
                entity.getMessageId(),
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
}
