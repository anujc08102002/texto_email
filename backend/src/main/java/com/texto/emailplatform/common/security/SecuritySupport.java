package com.texto.emailplatform.common.security;

import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * Helpers for reading the authenticated dashboard caller and asserting permissions.
 */
public final class SecuritySupport {

    private SecuritySupport() {
    }

    public static Optional<DashboardPrincipal> dashboardPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof DashboardPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public static DashboardPrincipal requireDashboardPrincipal(Authentication authentication) {
        return dashboardPrincipal(authentication)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED.value(),
                        "UNAUTHENTICATED",
                        "Authentication is required"
                ));
    }

    public static void requirePermission(DashboardPrincipal principal, String permission) {
        if (principal == null || !RolePermissions.has(principal.role(), permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access is denied");
        }
    }
}
