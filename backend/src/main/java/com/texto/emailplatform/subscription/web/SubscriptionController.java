package com.texto.emailplatform.subscription.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.subscription.SubscriptionService;
import com.texto.emailplatform.subscription.api.SubscriptionResponse;
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
@RequestMapping("/api/v1/subscription")
@Tag(name = "Subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    @Operation(summary = "Return the current subscription for the authenticated tenant")
    public ApiResponse<SubscriptionResponse> current(Authentication authentication) {
        SecuritySupport.requirePermission(
                SecuritySupport.requireDashboardPrincipal(authentication),
                Permission.SUBSCRIPTION_READ
        );
        return ApiResponse.ok(subscriptionService.getCurrent(currentTenantId()));
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
