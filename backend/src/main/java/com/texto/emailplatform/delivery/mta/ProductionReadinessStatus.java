package com.texto.emailplatform.delivery.mta;

/**
 * Explicit readiness states. {@code VERIFIED} requires an actual check, never configuration alone.
 */
public enum ProductionReadinessStatus {
    NOT_CONFIGURED,
    CONFIGURED,
    VERIFIED,
    BLOCKED
}
