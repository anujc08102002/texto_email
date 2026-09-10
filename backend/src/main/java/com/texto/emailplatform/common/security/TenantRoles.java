package com.texto.emailplatform.common.security;

/**
 * Roles a user can hold inside a tenant workspace.
 */
public final class TenantRoles {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";
    public static final String VIEWER = "VIEWER";

    private TenantRoles() {
    }
}
