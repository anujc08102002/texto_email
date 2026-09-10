package com.texto.emailplatform.usage.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.tenant.web.TenantContext;
import com.texto.emailplatform.usage.UsageService;
import com.texto.emailplatform.usage.api.UsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage")
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping
    @Operation(summary = "Return usage for the current billing period")
    public ApiResponse<UsageResponse> currentUsage(Authentication authentication) {
        SecuritySupport.requirePermission(
                SecuritySupport.requireDashboardPrincipal(authentication),
                Permission.USAGE_READ
        );
        return ApiResponse.ok(usageService.getUsage(currentTenantId()));
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
