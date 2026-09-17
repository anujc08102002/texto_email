package com.texto.emailplatform.complaint;

/**
 * Deterministic complaint decision. Does not parse MIME, look up tenants, or mutate bounce state.
 */
public enum ComplaintPolicyAction {
    NO_ACTION,
    SUPPRESS
}
