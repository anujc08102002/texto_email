package com.texto.emailplatform.template;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.PermissionAuthorizationManager;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.plan.FeatureCodes;
import com.texto.emailplatform.template.api.CreateTemplateRequest;
import com.texto.emailplatform.template.api.CreateTemplateVersionRequest;
import com.texto.emailplatform.template.api.PatchTemplateRequest;
import com.texto.emailplatform.template.api.TemplateResponse;
import com.texto.emailplatform.template.api.TemplateVersionResponse;
import com.texto.emailplatform.template.domain.TemplateEntity;
import com.texto.emailplatform.template.domain.TemplateRepository;
import com.texto.emailplatform.template.domain.TemplateVersionEntity;
import com.texto.emailplatform.template.domain.TemplateVersionRepository;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.usage.UsageMetrics;
import com.texto.emailplatform.usage.UsageService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateRenderer templateRenderer;
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final PermissionAuthorizationManager permissionAuthorizationManager;

    public TemplateService(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRenderer templateRenderer,
            EntitlementService entitlementService,
            UsageService usageService,
            PermissionAuthorizationManager permissionAuthorizationManager
    ) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateRenderer = templateRenderer;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.permissionAuthorizationManager = permissionAuthorizationManager;
    }

    @Transactional
    public TemplateResponse create(DashboardPrincipal principal, CreateTemplateRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_MANAGE);
        UUID tenantId = requireTenantId();
        requireFeature(tenantId);

        long activeCount = templateRepository.countByTenantIdAndStatusNot(tenantId, TemplateEntity.STATUS_ARCHIVED);
        OptionalLong limit = entitlementService.getLimit(tenantId, UsageMetrics.TEMPLATES);
        if (limit.isPresent() && activeCount + 1 > limit.getAsLong()) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "QUOTA_EXCEEDED",
                    "This plan allows at most " + limit.getAsLong() + " templates"
            );
        }

        String slug = uniqueSlug(tenantId, request.name());
        TemplateEntity template = templateRepository.save(TemplateEntity.create(
                tenantId,
                request.name().trim(),
                slug,
                blankToNull(request.description()),
                principal.userId()
        ));

        TemplateVersionEntity version = templateVersionRepository.save(TemplateVersionEntity.create(
                template.getId(),
                1,
                request.subject().trim(),
                request.htmlContent(),
                blankToNull(request.textContent()),
                request.variablesSchema(),
                principal.userId()
        ));
        template.setCurrentVersionId(version.getId());
        template.activate();
        templateRepository.save(template);

        usageService.recordUsage(
                tenantId,
                UsageMetrics.TEMPLATES,
                templateRepository.countByTenantIdAndStatusNot(tenantId, TemplateEntity.STATUS_ARCHIVED)
        );
        return toResponse(template, version.getVersion());
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(DashboardPrincipal principal) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_READ);
        return templateRepository.findByTenantIdOrderByCreatedAtDesc(requireTenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(DashboardPrincipal principal, UUID templateId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_READ);
        return toResponse(requireOwned(templateId));
    }

    @Transactional
    public TemplateResponse patch(DashboardPrincipal principal, UUID templateId, PatchTemplateRequest request) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_MANAGE);
        TemplateEntity template = requireOwned(templateId);
        if (TemplateEntity.STATUS_ARCHIVED.equals(template.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "TEMPLATE_ARCHIVED", "Archived templates cannot be updated");
        }
        template.patch(
                request.name() == null ? null : request.name().trim(),
                request.description()
        );
        return toResponse(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse archive(DashboardPrincipal principal, UUID templateId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_MANAGE);
        UUID tenantId = requireTenantId();
        TemplateEntity template = requireOwned(templateId);
        template.archive();
        templateRepository.save(template);
        usageService.recordUsage(
                tenantId,
                UsageMetrics.TEMPLATES,
                templateRepository.countByTenantIdAndStatusNot(tenantId, TemplateEntity.STATUS_ARCHIVED)
        );
        return toResponse(template);
    }

    @Transactional
    public void delete(DashboardPrincipal principal, UUID templateId) {
        archive(principal, templateId);
    }

    @Transactional
    public TemplateVersionResponse createVersion(
            DashboardPrincipal principal,
            UUID templateId,
            CreateTemplateVersionRequest request
    ) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_MANAGE);
        requireFeature(requireTenantId());
        TemplateEntity template = requireOwned(templateId);
        if (TemplateEntity.STATUS_ARCHIVED.equals(template.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "TEMPLATE_ARCHIVED", "Archived templates cannot gain versions");
        }
        int next = templateVersionRepository.findTopByTemplateIdOrderByVersionDesc(templateId)
                .map(version -> version.getVersion() + 1)
                .orElse(1);
        TemplateVersionEntity version = templateVersionRepository.save(TemplateVersionEntity.create(
                templateId,
                next,
                request.subject().trim(),
                request.htmlContent(),
                blankToNull(request.textContent()),
                request.variablesSchema(),
                principal.userId()
        ));
        return toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<TemplateVersionResponse> listVersions(DashboardPrincipal principal, UUID templateId) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_READ);
        requireOwned(templateId);
        return templateVersionRepository.findByTemplateIdOrderByVersionDesc(templateId).stream()
                .map(TemplateService::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateVersionResponse getVersion(DashboardPrincipal principal, UUID templateId, int versionNumber) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_READ);
        requireOwned(templateId);
        return toVersionResponse(requireVersion(templateId, versionNumber));
    }

    @Transactional
    public TemplateResponse activateVersion(DashboardPrincipal principal, UUID templateId, int versionNumber) {
        permissionAuthorizationManager.requirePermission(principal, Permission.TEMPLATE_MANAGE);
        TemplateEntity template = requireOwned(templateId);
        if (TemplateEntity.STATUS_ARCHIVED.equals(template.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "TEMPLATE_ARCHIVED", "Archived templates cannot activate versions");
        }
        TemplateVersionEntity version = requireVersion(templateId, versionNumber);
        template.setCurrentVersionId(version.getId());
        template.activate();
        return toResponse(templateRepository.save(template), version.getVersion());
    }

    @Transactional(readOnly = true)
    public TemplateVersionEntity requireRenderableVersion(UUID tenantId, UUID templateId, Integer versionNumber) {
        TemplateEntity template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "TEMPLATE_NOT_FOUND", "Template was not found"));
        if (TemplateEntity.STATUS_ARCHIVED.equals(template.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "TEMPLATE_ARCHIVED", "Template is archived");
        }
        if (versionNumber != null) {
            return requireVersion(templateId, versionNumber);
        }
        if (template.getCurrentVersionId() == null) {
            throw new ApiException(HttpStatus.CONFLICT.value(), "TEMPLATE_NO_VERSION", "Template has no active version");
        }
        return templateVersionRepository.findByIdAndTemplateId(template.getCurrentVersionId(), templateId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "TEMPLATE_VERSION_MISSING",
                        "Active template version is missing"
                ));
    }

    public TemplateRenderer.RenderedTemplate render(TemplateVersionEntity version, Map<String, Object> variables) {
        return templateRenderer.render(
                version.getSubject(),
                version.getHtmlContent(),
                version.getTextContent(),
                version.getVariablesSchema(),
                variables
        );
    }

    private TemplateEntity requireOwned(UUID templateId) {
        return templateRepository.findByIdAndTenantId(templateId, requireTenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND.value(), "TEMPLATE_NOT_FOUND", "Template was not found"));
    }

    private TemplateVersionEntity requireVersion(UUID templateId, int versionNumber) {
        return templateVersionRepository.findByTemplateIdAndVersion(templateId, versionNumber)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND.value(),
                        "TEMPLATE_VERSION_NOT_FOUND",
                        "Template version was not found"
                ));
    }

    private void requireFeature(UUID tenantId) {
        if (!entitlementService.canUseFeature(tenantId, FeatureCodes.TEMPLATES)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN.value(),
                    "FEATURE_NOT_AVAILABLE",
                    "Templates are not available on the current plan"
            );
        }
    }

    private String uniqueSlug(UUID tenantId, String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (templateRepository.existsByTenantIdAndSlug(tenantId, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slugify(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "template";
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-$", "");
        }
        return slug;
    }

    private TemplateResponse toResponse(TemplateEntity template) {
        Integer version = null;
        if (template.getCurrentVersionId() != null) {
            version = templateVersionRepository.findById(template.getCurrentVersionId())
                    .map(TemplateVersionEntity::getVersion)
                    .orElse(null);
        }
        return toResponse(template, version);
    }

    private static TemplateResponse toResponse(TemplateEntity template, Integer currentVersion) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getSlug(),
                template.getDescription(),
                template.getStatus(),
                template.getCurrentVersionId(),
                currentVersion,
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private static TemplateVersionResponse toVersionResponse(TemplateVersionEntity version) {
        return new TemplateVersionResponse(
                version.getId(),
                version.getTemplateId(),
                version.getVersion(),
                version.getSubject(),
                version.getHtmlContent(),
                version.getTextContent(),
                version.getVariablesSchema(),
                version.getCreatedAt()
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
