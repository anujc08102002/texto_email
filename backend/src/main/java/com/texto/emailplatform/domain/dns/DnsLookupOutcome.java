package com.texto.emailplatform.domain.dns;

/**
 * Outcome of a TXT lookup. These codes are for internal classification only;
 * API responses use customer-safe messages and never include resolver hosts.
 */
public enum DnsLookupOutcome {
    OK,
    MISSING,
    NXDOMAIN,
    TIMEOUT,
    TEMPORARY_FAILURE,
    MALFORMED
}
