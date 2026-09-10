package com.texto.emailplatform.domain;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.PermissionAuthorizationManager;
import com.texto.emailplatform.domain.api.CreateDomainRequest;
import com.texto.emailplatform.domain.api.DomainResponse;
import com.texto.emailplatform.domain.api.DomainVerificationResponse;
import com.texto.emailplatform.domain.api.DomainVerificationResponse.VerificationRecordResponse;
import com.texto.emailplatform.domain.domain.DomainEntity;
import com.texto.emailplatform.domain.domain.DomainRepository;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordEntity;
import com.texto.emailplatform.domain.domain.DomainVerificationRecordRepository;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.tenant.domain.TenantEntity;
import com.texto.emailplatform.tenant.domain.TenantRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.UsageService;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?!-)[a-z0-9-]+(\\.[a-z0-9-]+)+$"
    );

    private final DomainRepository domainRepository;
    private final DomainVerificationRecordRepository verificationRecordRepository;
    private final DomainVerificationService domainVerificationService;
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final PermissionAuthorizationManager permissionAuthorizationManager;
    private final TenantRepository tenantRepository;
    private final DomainVerificationRateLimiter domainVerificationRateLimiter;
    private final EmailPlatformProperties properties;

    public DomainService(
            DomainRepository domainRepository,
            DomainVerificationRecordRepository verificationRecordRepository,
            DomainVerificationService domainVerificationService,
            EntitlementService entitlementService,
            UsageService usageService,
            PermissionAuthorizationManager permissionAuthorizationManager,
            TenantRepository tenantRepository,
            DomainVerificationRateLimiter domainVerificationRateLimiter,
            EmailPlatformProperties properties
    ) {
        this.domainRepository = domainRepository;
        this.verificationRecordRepository = verificationRecordRepository;
        this.domainVerificationService = domainVerificationService;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.permissionAuthorizationManager = permissionAuthorizationManager;
        this.tenantRepository = tenantRepository;
        this.domainVerificationRateLimiter = domainVerificationRateLimiter;
        this.properties = properties;
    }

    @Transactional
    public DomainResponse create(DashboardPrincipal principal, CreateDomainRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.DOMAIN_MANAGE);
        UUID tenantId = requireTenantId();
        requireFeature(tenantId);

        String domain = DomainNormalizer.normalize(request.domain());
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "domain is invalid");
        }
        if (PlatformSenderDomains.isPlatformTestDomain(domain)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST.value(),
                    "VALIDATION_ERROR",
                    "Platform test domains are provisioned automatically"
            );
        }
        if (domainRepository.existsByTenantIdAndDomain(tenantId, domain)) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "DOMAIN_EXISTS", "Domain already exists for this tenant");
        }

        long count = customDomainCount(tenantId);
        OptionalLong limit = entitlementService.getLimit(tenantId, UsageMetrics.DOMAINS);
        if (limit.isPresent() && count + 1 > limit.getAsLong()) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "QUOTA_EXCEEDED",
                    "This plan allows at most " + limit.getAsLong() + " domains"
            );
        }

        DomainEntity entity = domainRepository.save(DomainEntity.create(tenantId, domain));
        domainVerificationService.ensureRecords(entity);
        usageService.recordUsage(tenantId, UsageMetrics.DOMAINS, customDomainCount(tenantId));
        return toResponse(entity);
    }

    @Transactional
    public List<DomainResponse> list(DashboardPrincipal principal) {
        permissionAuthorizationManager.requirePermission(principal, Permission.DOMAIN_READ);
        UUID tenantId = requireTenantId();
        ensurePlatformTestDomain(tenantId);
        return domainRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DomainResponse get(DashboardPrincipal principal, UUID domainId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.DOMAIN_READ);
        return toResponse(requireOwned(domainId));
    }

    @Transactional
    public void delete(DashboardPrincipal principal, UUID domainId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.DOMAIN_MANAGE);
        UUID tenantId = requireTenantId();
        DomainEntity entity = requireOwned(domainId);
        if (PlatformSenderDomains.isPlatformTestDomain(entity.getDomain())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "PLATFORM_DOMAIN_LOCKED",
                    "The platform test domain cannot be deleted"
            );
        }
        verificationRecordRepository.deleteByDomainId(entity.getId());
        domainRepository.delete(entity);
        usageService.recordUsage(tenantId, UsageMetrics.DOMAINS, customDomainCount(tenantId));
    }

    @Transactional
    public DomainResponse verify(DashboardPrincipal principal, UUID domainId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.DOMAIN_MANAGE);
        UUID tenantId = requireTenantId();
        requireFeature(tenantId);
        domainVerificationRateLimiter.check(tenantId, domainId);
        DomainEntity entity = requireOwned(domainId);
        if (PlatformSenderDomains.isPlatformTestDomain(entity.getDomain())) {
            domainVerificationService.markRecordsVerified(entity);
            entity.markVerified();
            return toResponse(domainRepository.save(entity));
        }
        entity.setStatus(DomainEntity.STATUS_VERIFYING);
        domainRepository.save(entity);

        boolean verified = domainVerificationService.verifyRecords(entity);
        entity.setStatus(verified ? DomainEntity.STATUS_VERIFIED : DomainEntity.STATUS_FAILED);
        return toResponse(domainRepository.save(entity));
    }

    @Transactional
    public DomainVerificationResponse getVerification(DashboardPrincipal principal, UUID domainId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.DOMAIN_READ);
        DomainEntity entity = requireOwned(domainId);
        domainVerificationService.ensureRecords(entity);
        List<DomainVerificationRecordEntity> stored = verificationRecordRepository
                .findByDomainIdOrderByTypeAsc(entity.getId());
        List<VerificationRecordResponse> records = stored.stream()
                .map(DomainService::toRecordResponse)
                .toList();
        return new DomainVerificationResponse(
                entity.getId(),
                entity.getDomain(),
                entity.getStatus(),
                statusOf(stored, DomainVerificationRecordEntity.TYPE_OWNERSHIP),
                statusOf(stored, DomainVerificationRecordEntity.TYPE_SPF),
                statusOf(stored, DomainVerificationRecordEntity.TYPE_DKIM),
                statusOf(stored, DomainVerificationRecordEntity.TYPE_DMARC),
                records
        );
    }

    /**
     * Ensures the From host is either a verified custom domain or an allowed platform test sender.
     */
    @Transactional
    public void requireVerifiedSender(UUID tenantId, String fromAddress) {
        String host = extractHost(fromAddress);
        if (host == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST.value(), "INVALID_FROM_ADDRESS", "from address is invalid");
        }

        if (properties.getDomains().isAllowPlatformTestSenders()) {
            TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                String allowed = PlatformSenderDomains.hostForSlug(tenant.getSlug());
                if (host.equals(allowed)) {
                    ensurePlatformTestDomain(tenantId);
                    return;
                }
            }
        }

        DomainEntity domain = domainRepository.findByTenantIdAndDomain(tenantId, host)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN.value(),
                        "UNVERIFIED_SENDER_DOMAIN",
                        "Sender domain is not registered for this tenant"
                ));
        if (!DomainEntity.STATUS_VERIFIED.equals(domain.getStatus())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "UNVERIFIED_SENDER_DOMAIN",
                    "Sender domain is not verified"
            );
        }
        domainVerificationService.ensureRecords(domain);
    }

    @Transactional
    public DomainEntity ensurePlatformTestDomain(UUID tenantId) {
        if (!properties.getDomains().isAllowPlatformTestSenders()) {
            return null;
        }
        TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }
        String host = PlatformSenderDomains.hostForSlug(tenant.getSlug());
        DomainEntity existing = domainRepository.findByTenantIdAndDomain(tenantId, host).orElse(null);
        if (existing != null) {
            domainVerificationService.ensureRecords(existing);
            if (!DomainEntity.STATUS_VERIFIED.equals(existing.getStatus())) {
                domainVerificationService.markRecordsVerified(existing);
                existing.markVerified();
                return domainRepository.save(existing);
            }
            return existing;
        }
        DomainEntity entity = DomainEntity.create(tenantId, host);
        entity.markVerified();
        entity = domainRepository.save(entity);
        domainVerificationService.markRecordsVerified(entity);
        return entity;
    }

    private long customDomainCount(UUID tenantId) {
        return domainRepository.countCustomDomains(tenantId, "%" + PlatformSenderDomains.SUFFIX);
    }

    private DomainEntity requireOwned(UUID domainId) {
        return domainRepository.findByIdAndTenantId(domainId, requireTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "DOMAIN_NOT_FOUND", "Domain was not found"));
    }

    private void requireFeature(UUID tenantId) {
        if (!entitlementService.canUseFeature(tenantId, FeatureCodes.CUSTOM_DOMAIN)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "FEATURE_NOT_AVAILABLE",
                    "Custom domains are not available on the current plan"
            );
        }
    }

    private static String extractHost(String fromAddress) {
        if (fromAddress == null || !fromAddress.contains("@")) {
            return null;
        }
        String host = fromAddress.substring(fromAddress.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
        return DomainNormalizer.normalize(host);
    }

    private DomainResponse toResponse(DomainEntity entity) {
        List<DomainVerificationRecordEntity> records =
                verificationRecordRepository.findByDomainIdOrderByTypeAsc(entity.getId());
        return new DomainResponse(
                entity.getId(),
                entity.getDomain(),
                entity.getStatus(),
                entity.getVerificationStatus(),
                statusOf(records, DomainVerificationRecordEntity.TYPE_OWNERSHIP),
                statusOf(records, DomainVerificationRecordEntity.TYPE_SPF),
                statusOf(records, DomainVerificationRecordEntity.TYPE_DKIM),
                statusOf(records, DomainVerificationRecordEntity.TYPE_DMARC),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String statusOf(List<DomainVerificationRecordEntity> records, String type) {
        return records.stream()
                .filter(record -> type.equals(record.getType()))
                .map(DomainVerificationRecordEntity::getStatus)
                .findFirst()
                .orElse("PENDING");
    }

    private static VerificationRecordResponse toRecordResponse(DomainVerificationRecordEntity record) {
        return new VerificationRecordResponse(
                record.getId(),
                record.getType(),
                record.getName(),
                record.getValue(),
                record.getStatus(),
                DomainVerificationService.customerDetail(record),
                record.getSelector(),
                record.getPublicKey(),
                record.getVerifiedAt()
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
