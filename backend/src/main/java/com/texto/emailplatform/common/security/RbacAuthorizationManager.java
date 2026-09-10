package com.texto.emailplatform.common.security;

/**
 * Authorization contract for dashboard and API-key callers.
 * See {@link PermissionAuthorizationManager} for the role-based evaluation.
 */
@FunctionalInterface
public interface RbacAuthorizationManager {

    boolean hasPermission(String tenantId, String principalId, String permission);
}
