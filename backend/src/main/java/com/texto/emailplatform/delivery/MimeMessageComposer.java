package com.texto.emailplatform.delivery;

import com.texto.emailplatform.bounce.BounceAddress;
import com.texto.emailplatform.bounce.BounceCorrelationToken;
import com.texto.emailplatform.delivery.mta.MtaSubmitRequest;
import com.texto.emailplatform.delivery.mta.SmtpEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Builds RFC 822 bytes (including DKIM-Signature) before MTA handoff.
 */
final class MimeMessageComposer {

    private static final String CRLF = "\r\n";

    private final DkimSigningService dkimSigningService;
    private final String bounceDomain;
    private final String messageIdDomain;

    MimeMessageComposer(DkimSigningService dkimSigningService, String bounceDomain) {
        this(dkimSigningService, bounceDomain, "texto.local");
    }

    MimeMessageComposer(DkimSigningService dkimSigningService, String bounceDomain, String messageIdDomain) {
        this.dkimSigningService = dkimSigningService;
        this.bounceDomain = bounceDomain;
        this.messageIdDomain = messageIdDomain == null || messageIdDomain.isBlank() ? "texto.local" : messageIdDomain.trim();
    }

    Composed compose(DeliveryEngine.DeliveryRequest request) {
        List<String> recipients = allRecipients(request);
        boolean hasHtml = request.htmlBody() != null && !request.htmlBody().isBlank();
        boolean hasText = request.textBody() != null && !request.textBody().isBlank();
        String boundary = hasHtml && hasText ? "texto-" + UUID.randomUUID() : null;
        String contentType;
        if (hasHtml && hasText) {
            contentType = "multipart/alternative; boundary=\"" + boundary + "\"";
        } else if (hasHtml) {
            contentType = "text/html; charset=UTF-8";
        } else {
            contentType = "text/plain; charset=UTF-8";
        }

        String messageId = "<" + UUID.randomUUID() + "@" + messageIdDomain + ">";
        LinkedHashMap<String, String> signedHeaders = new LinkedHashMap<>();
        signedHeaders.put("from", headerValue(request.from()));
        signedHeaders.put("to", headerValue(String.join(", ", nullToEmpty(request.to()))));
        if (request.cc() != null && !request.cc().isEmpty()) {
            signedHeaders.put("cc", headerValue(String.join(", ", request.cc())));
        }
        if (request.replyTo() != null && !request.replyTo().isBlank()) {
            signedHeaders.put("reply-to", headerValue(request.replyTo()));
        }
        signedHeaders.put("subject", headerValue(request.subject()));
        signedHeaders.put("date", DkimSigner.dateHeader(java.time.Instant.now()));
        signedHeaders.put("message-id", messageId);
        signedHeaders.put("mime-version", "1.0");
        signedHeaders.put("content-type", contentType);

        String body = renderBody(request, boundary, hasHtml, hasText);
        String dkim = dkimSigningService.sign(request.tenantId(), request.from(), signedHeaders, body);
        if (dkim == null || !dkim.contains("DKIM-Signature:")) {
            throw new DkimSigningException("Unable to DKIM-sign the message");
        }

        StringBuilder rfc822 = new StringBuilder();
        rfc822.append(dkim);
        if (!dkim.endsWith(CRLF)) {
            rfc822.append(CRLF);
        }
        appendHeader(rfc822, "From", signedHeaders.get("from"));
        appendHeader(rfc822, "To", signedHeaders.get("to"));
        if (signedHeaders.containsKey("cc")) {
            appendHeader(rfc822, "Cc", signedHeaders.get("cc"));
        }
        if (signedHeaders.containsKey("reply-to")) {
            appendHeader(rfc822, "Reply-To", signedHeaders.get("reply-to"));
        }
        appendHeader(rfc822, "Subject", signedHeaders.get("subject"));
        appendHeader(rfc822, "Date", signedHeaders.get("date"));
        appendHeader(rfc822, "Message-ID", signedHeaders.get("message-id"));
        appendHeader(rfc822, "MIME-Version", signedHeaders.get("mime-version"));
        appendHeader(rfc822, "Content-Type", signedHeaders.get("content-type"));
        if (!hasHtml || !hasText) {
            appendHeader(rfc822, "Content-Transfer-Encoding", "8bit");
        }
        rfc822.append(CRLF);
        rfc822.append(body.replace("\n", CRLF));

        String mailFrom = BounceAddress.mailFromOrFallback(
                request.bounceCorrelationToken(),
                bounceDomain,
                request.from()
        );
        String envelopeId = BounceCorrelationToken.normalize(request.bounceCorrelationToken());
        SmtpEnvelope envelope = new SmtpEnvelope(mailFrom, recipients, envelopeId);
        return new Composed(new MtaSubmitRequest(envelope, rfc822.toString().getBytes(StandardCharsets.UTF_8)), messageId);
    }

    private static void appendHeader(StringBuilder rfc822, String name, String value) {
        rfc822.append(name).append(": ").append(value).append(CRLF);
    }

    static List<String> allRecipients(DeliveryEngine.DeliveryRequest request) {
        List<String> recipients = new ArrayList<>();
        if (request.to() != null) {
            recipients.addAll(request.to());
        }
        if (request.cc() != null) {
            recipients.addAll(request.cc());
        }
        if (request.bcc() != null) {
            recipients.addAll(request.bcc());
        }
        return recipients;
    }

    private static String renderBody(DeliveryEngine.DeliveryRequest request, String boundary, boolean hasHtml, boolean hasText) {
        StringBuilder body = new StringBuilder();
        if (hasHtml && hasText) {
            appendLine(body, "--" + boundary);
            appendLine(body, "Content-Type: text/plain; charset=UTF-8");
            appendLine(body, "Content-Transfer-Encoding: 8bit");
            appendLine(body, "");
            appendBody(body, request.textBody());
            appendLine(body, "--" + boundary);
            appendLine(body, "Content-Type: text/html; charset=UTF-8");
            appendLine(body, "Content-Transfer-Encoding: 8bit");
            appendLine(body, "");
            appendBody(body, request.htmlBody());
            appendLine(body, "--" + boundary + "--");
            return body.toString();
        }
        appendBody(body, hasHtml ? request.htmlBody() : hasText ? request.textBody() : "");
        return body.toString();
    }

    private static void appendLine(StringBuilder body, String line) {
        body.append(line).append('\n');
    }

    private static void appendBody(StringBuilder body, String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        body.append(normalized);
        if (!normalized.endsWith("\n")) {
            body.append('\n');
        }
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String headerValue(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", " ");
    }

    record Composed(MtaSubmitRequest request, String messageId) {
    }
}
