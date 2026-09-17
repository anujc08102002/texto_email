package com.texto.emailplatform.bounce;

/**
 * Deterministic DSN application decision. Does not parse MIME or look up tenants.
 */
public enum BouncePolicyAction {
    NO_ACTION,
    DEFER,
    BOUNCE,
    SUPPRESS
}
