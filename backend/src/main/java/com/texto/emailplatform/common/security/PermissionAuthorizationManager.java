package com.texto.emailplatform.common.security;

import com.texto.emailplatform.auth.domain.UserRepository;
import com.texto.emailplatform.auth.security.DashboardPrincipal;
import com.texto.emailplatform.common.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates tenant-scoped permissions from the role stored on the user record.
 */
@Component
public class PermissionAuthorizationManager implements RbacAuthorizationManager {

    private final UserRepository userRepository;

    public PermissionAuthorizationManager(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(String tenantId, String principalId, String permission) {
        UUID tenant = parseUuid(tenantId);
        UUID principal = parseUuid(principalId);
        if (tenant == null || principal == null || permission == null) {
            return false;
        }
        return userRepository.findById(principal)
                .filter(user -> tenant.equals(user.getTenantId()))
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(user -> RolePermissions.has(user.getRole(), permission))
                .orElse(false);
    }

    public boolean hasPermission(DashboardPrincipal principal, String permission) {
        return principal != null && RolePermissions.has(principal.role(), permission);
    }

    public void requirePermission(DashboardPrincipal principal, String permission) {
        if (!hasPermission(principal, permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access is denied");
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
