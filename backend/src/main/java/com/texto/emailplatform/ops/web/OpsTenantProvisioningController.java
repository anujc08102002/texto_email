package com.texto.emailplatform.ops.web;

import com.texto.emailplatform.auth.AuthService;
import com.texto.emailplatform.auth.api.AuthUserResponse;
import com.texto.emailplatform.auth.api.RegisterRequest;
import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.ops.security.OpsAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tenants")
public class OpsTenantProvisioningController {
    private final AuthService authService;

    public OpsTenantProvisioningController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ApiResponse<AuthUserResponse> create(Authentication authentication, @Valid @RequestBody CreateTenantRequest request) {
        OpsAuthorization.require(authentication);
        return ApiResponse.ok(authService.provisionTenant(new RegisterRequest(request.organization(), request.email(), request.password()), request.planCode()));
    }
    public record CreateTenantRequest(
            @NotBlank @Size(max = 255) String organization,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 64) String planCode
    ) {}
}
