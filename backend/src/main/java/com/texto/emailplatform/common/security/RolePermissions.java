package com.texto.emailplatform.common.security;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Static role to permission matrix. Roles are stored on the user record as plain strings.
 */
public final class RolePermissions {

    private static final Set<String> READ_ONLY = Set.of(
            Permission.API_KEY_READ,
            Permission.SUBSCRIPTION_READ,
            Permission.ENTITLEMENT_READ,
            Permission.USAGE_READ,
            Permission.EMAIL_READ,
            Permission.DOMAIN_READ,
            Permission.TEMPLATE_READ,
            Permission.WEBHOOK_READ,
            Permission.SUPPRESSION_READ,
            Permission.MEMBER_READ
    );

    private static final Set<String> MEMBER = union(READ_ONLY, Set.of(
            Permission.EMAIL_SEND,
            Permission.TEMPLATE_MANAGE,
            Permission.SUPPRESSION_MANAGE
    ));

    private static final Set<String> ADMIN = union(MEMBER, Set.of(
            Permission.API_KEY_CREATE,
            Permission.API_KEY_REVOKE,
            Permission.DOMAIN_MANAGE,
            Permission.WEBHOOK_MANAGE,
            Permission.MEMBER_MANAGE,
            Permission.BILLING_READ
    ));

    private static final Set<String> OWNER = union(ADMIN, Set.of(
            Permission.SUBSCRIPTION_MANAGE,
            Permission.BILLING_MANAGE
    ));

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            TenantRoles.OWNER, OWNER,
            TenantRoles.ADMIN, ADMIN,
            TenantRoles.MEMBER, MEMBER,
            TenantRoles.VIEWER, READ_ONLY
    );

    private RolePermissions() {
    }

    public static Set<String> forRole(String role) {
        if (role == null || role.isBlank()) {
            return Set.of();
        }
        return BY_ROLE.getOrDefault(role.trim().toUpperCase(Locale.ROOT), Set.of());
    }

    public static boolean has(String role, String permission) {
        return permission != null && forRole(role).contains(permission);
    }

    private static Set<String> union(Set<String> base, Set<String> additions) {
        return Set.copyOf(java.util.stream.Stream.concat(base.stream(), additions.stream()).toList());
    }
}
