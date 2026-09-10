package com.texto.emailplatform.auth.web;

import static com.texto.emailplatform.auth.security.DashboardSessionAuthenticationFilter.BEARER_PREFIX;

import com.texto.emailplatform.auth.AuthService;
import com.texto.emailplatform.auth.api.AuthResponse;
import com.texto.emailplatform.auth.api.AuthUserResponse;
import com.texto.emailplatform.auth.api.LoginRequest;
import com.texto.emailplatform.auth.api.RegisterRequest;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.common.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a tenant and owner account")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current dashboard session")
    public ApiResponse<Void> logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authService.logout(bearerToken(authorizationHeader));
        return ApiResponse.<Void>ok(null);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated dashboard user")
    public ApiResponse<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof DashboardPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required");
        }
        return ApiResponse.ok(authService.currentUser(principal));
    }

    private static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }
}
