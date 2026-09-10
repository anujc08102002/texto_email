package com.texto.emailplatform.entitlement.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.entitlement.EntitlementService;
import com.texto.emailplatform.entitlement.api.EntitlementsResponse;
import com.texto.emailplatform.tenant.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entitlements")
@Tag(name = "Entitlements")
public class EntitlementController {

    private final EntitlementService entitlementService;

    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping
    @Operation(summary = "Return effective features, limits and usage for the current tenant")
    public ApiResponse<EntitlementsResponse> entitlements(Authentication authentication) {
        SecuritySupport.requirePermission(
                SecuritySupport.requireDashboardPrincipal(authentication),
                Permission.ENTITLEMENT_READ
        );
        return ApiResponse.ok(entitlementService.getEntitlements(currentTenantId()));
    }

    private static UUID currentTenantId() {
        return TenantContext.get()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHENTICATED",
                        "Authentication is required"
                ));
    }
}
