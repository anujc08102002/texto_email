package com.texto.emailplatform.template.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.template.TemplateService;
import com.texto.emailplatform.template.api.CreateTemplateRequest;
import com.texto.emailplatform.template.api.CreateTemplateVersionRequest;
import com.texto.emailplatform.template.api.PatchTemplateRequest;
import com.texto.emailplatform.template.api.TemplateResponse;
import com.texto.emailplatform.template.api.TemplateVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @Operation(summary = "Create a template with an initial version")
    public ApiResponse<TemplateResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateTemplateRequest request
    ) {
        return ApiResponse.ok(templateService.create(SecuritySupport.requireDashboardPrincipal(authentication), request));
    }

    @GetMapping
    @Operation(summary = "List templates for the current tenant")
    public ApiResponse<List<TemplateResponse>> list(Authentication authentication) {
        return ApiResponse.ok(templateService.list(SecuritySupport.requireDashboardPrincipal(authentication)));
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Get a template")
    public ApiResponse<TemplateResponse> get(Authentication authentication, @PathVariable UUID templateId) {
        return ApiResponse.ok(templateService.get(SecuritySupport.requireDashboardPrincipal(authentication), templateId));
    }

    @PatchMapping("/{templateId}")
    @Operation(summary = "Patch template metadata")
    public ApiResponse<TemplateResponse> patch(
            Authentication authentication,
            @PathVariable UUID templateId,
            @Valid @RequestBody PatchTemplateRequest request
    ) {
        return ApiResponse.ok(templateService.patch(
                SecuritySupport.requireDashboardPrincipal(authentication),
                templateId,
                request
        ));
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "Archive (soft-delete) a template")
    public ApiResponse<TemplateResponse> delete(Authentication authentication, @PathVariable UUID templateId) {
        templateService.delete(SecuritySupport.requireDashboardPrincipal(authentication), templateId);
        return ApiResponse.ok(templateService.get(SecuritySupport.requireDashboardPrincipal(authentication), templateId));
    }

    @PostMapping("/{templateId}/archive")
    @Operation(summary = "Archive a template")
    public ApiResponse<TemplateResponse> archive(Authentication authentication, @PathVariable UUID templateId) {
        return ApiResponse.ok(templateService.archive(
                SecuritySupport.requireDashboardPrincipal(authentication),
                templateId
        ));
    }

    @PostMapping("/{templateId}/versions")
    @Operation(summary = "Create a new immutable template version")
    public ApiResponse<TemplateVersionResponse> createVersion(
            Authentication authentication,
            @PathVariable UUID templateId,
            @Valid @RequestBody CreateTemplateVersionRequest request
    ) {
        return ApiResponse.ok(templateService.createVersion(
                SecuritySupport.requireDashboardPrincipal(authentication),
                templateId,
                request
        ));
    }

    @GetMapping("/{templateId}/versions")
    @Operation(summary = "List template versions")
    public ApiResponse<List<TemplateVersionResponse>> listVersions(
            Authentication authentication,
            @PathVariable UUID templateId
    ) {
        return ApiResponse.ok(templateService.listVersions(
                SecuritySupport.requireDashboardPrincipal(authentication),
                templateId
        ));
    }

    @GetMapping("/{templateId}/versions/{version}")
    @Operation(summary = "Get a template version")
    public ApiResponse<TemplateVersionResponse> getVersion(
            Authentication authentication,
            @PathVariable UUID templateId,
            @PathVariable int version
    ) {
        return ApiResponse.ok(templateService.getVersion(
                SecuritySupport.requireDashboardPrincipal(authentication),
                templateId,
                version
        ));
    }

    @PostMapping("/{templateId}/versions/{version}/activate")
    @Operation(summary = "Activate a template version")
    public ApiResponse<TemplateResponse> activateVersion(
            Authentication authentication,
            @PathVariable UUID templateId,
            @PathVariable int version
    ) {
        return ApiResponse.ok(templateService.activateVersion(
                SecuritySupport.requireDashboardPrincipal(authentication),
                templateId,
                version
        ));
    }
}
