package com.texto.emailplatform.bounce;

/**
 * Classification of one DSN recipient block. Preserves the original RFC 3463 status string.
 */
public record BounceClassification(
        BounceClass bounceClass,
        BounceFailureKind failureKind,
        DsnAction action,
        String statusCode
) {
}
