package com.texto.emailplatform.delivery;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the RFC 822 payload that is later DKIM-signed and handed to {@code MtaClient}.
 */
public final class OutboundMimeComposer {

    private static final DateTimeFormatter RFC5322_DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .withZone(ZoneOffset.UTC);

    private OutboundMimeComposer() {
    }

    public static ComposedMessage compose(DeliveryEngine.DeliveryRequest request, Instant now) {
        List<String> to = nullToEmpty(request.to());
        List<String> cc = nullToEmpty(request.cc());
        List<String> bcc = nullToEmpty(request.bcc());
        List<String> envelopeRecipients = new ArrayList<>();
        envelopeRecipients.addAll(to);
        envelopeRecipients.addAll(cc);
        envelopeRecipients.addAll(bcc);

        String from = headerValue(request.from());
        String domain = domainOf(from);
        String messageId = "<" + UUID.randomUUID() + "@" + domain + ">";

        StringBuilder headers = new StringBuilder();
        append(headers, "From", from);
        append(headers, "To", headerValue(String.join(", ", to)));
        if (!cc.isEmpty()) {
            append(headers, "Cc", headerValue(String.join(", ", cc)));
        }
        if (request.replyTo() != null && !request.replyTo().isBlank()) {
            append(headers, "Reply-To", headerValue(request.replyTo()));
        }
        append(headers, "Subject", headerValue(request.subject()));
        append(headers, "Date", RFC5322_DATE.format(now));
        append(headers, "Message-ID", messageId);
        append(headers, "MIME-Version", "1.0");

        boolean hasHtml = request.htmlBody() != null && !request.htmlBody().isBlank();
        boolean hasText = request.textBody() != null && !request.textBody().isBlank();
        StringBuilder body = new StringBuilder();
        if (hasHtml && hasText) {
            String boundary = "texto-" + UUID.randomUUID();
            append(headers, "Content-Type", "multipart/alternative; boundary=\"" + boundary + "\"");
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Type: text/plain; charset=UTF-8\r\n");
            body.append("Content-Transfer-Encoding: 8bit\r\n\r\n");
            body.append(crlf(request.textBody())).append("\r\n");
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Type: text/html; charset=UTF-8\r\n");
            body.append("Content-Transfer-Encoding: 8bit\r\n\r\n");
            body.append(crlf(request.htmlBody())).append("\r\n");
            body.append("--").append(boundary).append("--\r\n");
        } else if (hasHtml) {
            append(headers, "Content-Type", "text/html; charset=UTF-8");
            append(headers, "Content-Transfer-Encoding", "8bit");
            body.append(crlf(request.htmlBody()));
        } else {
            append(headers, "Content-Type", "text/plain; charset=UTF-8");
            append(headers, "Content-Transfer-Encoding", "8bit");
            body.append(hasText ? crlf(request.textBody()) : "");
        }

        String rfc822 = headers + "\r\n" + body;
        if (!rfc822.endsWith("\r\n")) {
            rfc822 = rfc822 + "\r\n";
        }
        return new ComposedMessage(
                from,
                envelopeRecipients,
                messageId,
                rfc822.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void append(StringBuilder headers, String name, String value) {
        headers.append(name).append(": ").append(value).append("\r\n");
    }

    private static String crlf(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String headerValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n]", " ");
    }

    static String domainOf(String from) {
        if (from == null || !from.contains("@")) {
            return "texto.local";
        }
        return from.substring(from.lastIndexOf('@') + 1).trim().toLowerCase(Locale.ROOT);
    }

    public record ComposedMessage(
            String mailFrom,
            List<String> envelopeRecipients,
            String messageId,
            byte[] rfc822
    ) {
    }
}
