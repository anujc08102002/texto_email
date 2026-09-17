package com.texto.emailplatform.bounce;

/**
 * Finer diagnostic category. Used later for suppression policy; this step only records it.
 */
public enum BounceFailureKind {
    PERMANENT_RECIPIENT,
    TEMPORARY_RECIPIENT,
    POLICY_REJECTION,
    MAILBOX_UNAVAILABLE,
    ADDRESS_RELATED,
    UNKNOWN
}
