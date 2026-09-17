package com.texto.emailplatform.bounce;

/**
 * Bounce/DSN ingestion module. Parses RFC 3464 reports, correlates via an opaque
 * bounce token, applies bounce policy (state + suppression), and persists events.
 * Trusted Postfix inbound is local/test only. There is no public bounce API.
 */
public final class BounceModule {

    private BounceModule() {
    }
}
