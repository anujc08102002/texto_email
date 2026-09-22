package com.texto.emailplatform.ops.web;

import static com.texto.emailplatform.auth.security.DashboardSessionAuthenticationFilter.BEARER_PREFIX;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.ops.OpsAuthService;
import com.texto.emailplatform.ops.api.OpsAdminResponse;
import com.texto.emailplatform.ops.api.OpsAuthResponse;
import com.texto.emailplatform.ops.api.OpsLoginRequest;
import com.texto.emailplatform.ops.security.OpsAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Ops auth")
public class OpsAuthController {

    private final OpsAuthService opsAuthService;

    public OpsAuthController(OpsAuthService opsAuthService) {
        this.opsAuthService = opsAuthService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a platform administrator")
    public ApiResponse<OpsAuthResponse> login(@Valid @RequestBody OpsLoginRequest request) {
        return ApiResponse.ok(opsAuthService.login(request.email(), request.password()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current platform-admin session")
    public ApiResponse<Void> logout(
            Authentication authentication,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        OpsAuthorization.require(authentication);
        opsAuthService.logout(bearer(authorization));
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated platform administrator")
    public ApiResponse<OpsAdminResponse> me(Authentication authentication) {
        return ApiResponse.ok(opsAuthService.current(OpsAuthorization.require(authentication)));
    }

    private static String bearer(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }
}
