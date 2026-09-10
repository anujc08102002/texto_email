package com.texto.emailplatform.billing.web;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.billing.BillingCheckoutService;
import com.texto.emailplatform.billing.api.BillingConfigResponse;
import com.texto.emailplatform.billing.api.CancelBillingRequest;
import com.texto.emailplatform.billing.api.ChangePlanRequest;
import com.texto.emailplatform.billing.api.CheckoutRequest;
import com.texto.emailplatform.billing.api.CheckoutResponse;
import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.common.security.Permission;
import com.texto.emailplatform.common.security.RolePermissions;
import com.texto.emailplatform.common.security.SecuritySupport;
import com.texto.emailplatform.subscription.api.SubscriptionResponse;
import com.texto.emailplatform.tenant.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing")
public class BillingController {

    private final BillingCheckoutService billingCheckoutService;

    public BillingController(BillingCheckoutService billingCheckoutService) {
        this.billingCheckoutService = billingCheckoutService;
    }

    @GetMapping("/config")
    @Operation(summary = "Public-safe billing configuration (never includes secrets)")
    public ApiResponse<BillingConfigResponse> config() {
        return ApiResponse.ok(billingCheckoutService.getConfig());
    }

    @PostMapping("/checkout")
    @Operation(summary = "Start paid-plan checkout with the configured billing provider")
    public ApiResponse<CheckoutResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request
    ) {
        DashboardPrincipal principal = requireBillingManager(authentication);
        return ApiResponse.ok(billingCheckoutService.createCheckout(
                currentTenantId(),
                request.planCode(),
                principal.email(),
                principal.email()
        ));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel the provider subscription")
    public ApiResponse<SubscriptionResponse> cancel(
            Authentication authentication,
            @Valid @RequestBody CancelBillingRequest request
    ) {
        requireBillingManager(authentication);
        return ApiResponse.ok(billingCheckoutService.cancel(currentTenantId(), Boolean.TRUE.equals(request.atPeriodEnd())));
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause the provider subscription")
    public ApiResponse<SubscriptionResponse> pause(Authentication authentication) {
        requireBillingManager(authentication);
        return ApiResponse.ok(billingCheckoutService.pause(currentTenantId()));
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume a paused provider subscription")
    public ApiResponse<SubscriptionResponse> resume(Authentication authentication) {
        requireBillingManager(authentication);
        return ApiResponse.ok(billingCheckoutService.resume(currentTenantId()));
    }

    @PostMapping("/change-plan")
    @Operation(summary = "Change plan at the provider; entitlements update after webhook confirmation")
    public ApiResponse<SubscriptionResponse> changePlan(
            Authentication authentication,
            @Valid @RequestBody ChangePlanRequest request
    ) {
        requireBillingManager(authentication);
        return ApiResponse.ok(billingCheckoutService.changePlan(currentTenantId(), request.planCode()));
    }

    private static DashboardPrincipal requireBillingManager(Authentication authentication) {
        DashboardPrincipal principal = SecuritySupport.requireDashboardPrincipal(authentication);
        boolean allowed = RolePermissions.has(principal.role(), Permission.BILLING_MANAGE)
                || RolePermissions.has(principal.role(), Permission.SUBSCRIPTION_MANAGE);
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access is denied");
        }
        return principal;
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
