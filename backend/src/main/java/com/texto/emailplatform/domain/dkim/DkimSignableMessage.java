package com.texto.emailplatform.domain.dkim;

import java.util.ArrayList;
import java.util.List;

/**
 * The finalized message content presented to {@link DkimSigner}: the ordered header fields and the
 * exact body that will be transmitted. The signer treats this as opaque input — it performs no MIME
 * construction, no I/O, and no domain resolution.
 *
 * <p>Header values MUST already be sanitized (no CR/LF) and the body MUST be the exact octets that
 * will be sent (before SMTP dot-stuffing, which is transport-only and removed by receivers).
 */
public final class DkimSignableMessage {

    public record Header(String name, String value) {
    }

    private final List<Header> headers;
    private final String body;

    public DkimSignableMessage(List<Header> headers, String body) {
        this.headers = List.copyOf(headers);
        this.body = body == null ? "" : body;
    }

    public List<Header> headers() {
        return headers;
    }

    public String body() {
        return body;
    }

    /** Returns the header values (in message order) whose name matches {@code name}, case-insensitively. */
    public List<Header> headersNamed(String name) {
        List<Header> matches = new ArrayList<>();
        for (Header header : headers) {
            if (header.name().equalsIgnoreCase(name)) {
                matches.add(header);
            }
        }
        return matches;
    }
}
