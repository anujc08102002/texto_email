package com.texto.emailplatform.ops.security;

import com.texto.emailplatform.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class OpsAuthorization {

    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    private OpsAuthorization() {
    }

    public static PlatformAdminPrincipal require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required");
        }
        if (authentication.getPrincipal() instanceof PlatformAdminPrincipal principal) {
            return principal;
        }
        throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Platform admin authorization is required");
    }
}
