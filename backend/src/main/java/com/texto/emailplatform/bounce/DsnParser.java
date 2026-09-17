package com.texto.emailplatform.bounce;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MailDateFormat;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Parses RFC 3464 {@code multipart/report; report-type=delivery-status} using Jakarta Mail.
 * Untrusted input: bounded size, part count, depth, and field lengths. Never renders HTML.
 */
@Component
public class DsnParser {

    private static final Session SESSION = Session.getInstance(sessionProperties());

    private final EmailPlatformProperties properties;

    public DsnParser(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    public ParsedDsn parse(byte[] rawRfc822) {
        try {
            return parseInternal(rawRfc822);
        } catch (Exception exception) {
            return ParsedDsn.failure("malformed_mime");
        }
    }

    private ParsedDsn parseInternal(byte[] rawRfc822) throws MessagingException, IOException {
        var limits = properties.getBounce();
        if (rawRfc822 == null || rawRfc822.length == 0) {
            return ParsedDsn.failure("empty");
        }
        if (rawRfc822.length > limits.getMaxRfc822Bytes()) {
            return ParsedDsn.failure("oversized");
        }

        MimeMessage message = new MimeMessage(SESSION, new ByteArrayInputStream(rawRfc822));
        int parts = countParts(message, 0, limits.getMaxMimeDepth(), limits.getMaxMimeParts());
        if (parts < 0) {
            return ParsedDsn.failure("too_many_parts");
        }

        BodyPart deliveryStatus = findPart(message, "message/delivery-status", 0, limits.getMaxMimeDepth());
        if (deliveryStatus == null) {
            return ParsedDsn.failure("missing_delivery_status");
        }

        String statusBody = readText(deliveryStatus, limits.getMaxRfc822Bytes());
        if (statusBody == null) {
            return ParsedDsn.failure("unreadable_delivery_status");
        }

        List<String> blocks = splitHeaderBlocks(statusBody);
        if (blocks.isEmpty()) {
            return ParsedDsn.failure("empty_delivery_status");
        }

        InternetHeaders perMessage = headersOf(blocks.getFirst());
        String reportingMta = typedValue(header(perMessage, "Reporting-MTA"), limits.getMaxHeaderLength());
        Instant arrivalDate = parseDate(header(perMessage, "Arrival-Date"));
        String originalEnvelopeId = clip(header(perMessage, "Original-Envelope-ID"), limits.getMaxHeaderLength());

        String originalMessageId = clip(extractOriginalMessageId(message, limits), limits.getMaxHeaderLength());
        String originalSender = clip(extractOriginalSender(message, limits), limits.getMaxHeaderLength());
        String reportTo = clip(firstHeader(message, "To"), limits.getMaxHeaderLength());

        int maxRecipients = Math.max(1, limits.getMaxRecipients());
        List<DsnRecipient> recipients = new ArrayList<>();
        for (int i = 1; i < blocks.size() && recipients.size() < maxRecipients; i++) {
            InternetHeaders recipientHeaders = headersOf(blocks.get(i));
            if (header(recipientHeaders, "Final-Recipient") == null
                    && header(recipientHeaders, "Original-Recipient") == null
                    && header(recipientHeaders, "Action") == null) {
                continue;
            }
            recipients.add(toRecipient(recipientHeaders, limits));
        }

        return ParsedDsn.success(
                reportingMta,
                arrivalDate,
                originalEnvelopeId,
                originalMessageId,
                originalSender,
                reportTo,
                recipients
        );
    }

    private DsnRecipient toRecipient(InternetHeaders headers, EmailPlatformProperties.Bounce limits) {
        String originalRecipient = addressValue(header(headers, "Original-Recipient"), limits.getMaxHeaderLength());
        String finalRecipient = addressValue(header(headers, "Final-Recipient"), limits.getMaxHeaderLength());
        String action = clip(header(headers, "Action"), 32);
        String status = clip(header(headers, "Status"), 32);
        String remoteMta = typedValue(header(headers, "Remote-MTA"), limits.getMaxHeaderLength());
        String diagnosticRaw = header(headers, "Diagnostic-Code");
        String diagnosticType = typedPrefix(diagnosticRaw);
        String diagnosticMessage = clip(typedValue(diagnosticRaw, limits.getMaxDiagnosticLength()), limits.getMaxDiagnosticLength());
        Instant lastAttempt = parseDate(header(headers, "Last-Attempt-Date"));
        Instant willRetryUntil = parseDate(header(headers, "Will-Retry-Until"));
        BounceClassification classification = BounceClassifier.classify(action, status, diagnosticMessage);
        return new DsnRecipient(
                originalRecipient,
                finalRecipient,
                action,
                status,
                remoteMta,
                clip(diagnosticType, 64),
                diagnosticMessage,
                lastAttempt,
                willRetryUntil,
                classification
        );
    }

    private String extractOriginalMessageId(MimeMessage message, EmailPlatformProperties.Bounce limits)
            throws MessagingException, IOException {
        BodyPart rfc822 = findPart(message, "message/rfc822", 0, limits.getMaxMimeDepth());
        if (rfc822 == null) {
            rfc822 = findPart(message, "text/rfc822-headers", 0, limits.getMaxMimeDepth());
        }
        if (rfc822 != null) {
            Object content = rfc822.getContent();
            if (content instanceof MimeMessage original) {
                String[] ids = original.getHeader("Message-ID");
                if (ids != null && ids.length > 0) {
                    return ids[0];
                }
            } else if (content instanceof String headers) {
                InternetHeaders parsed = headersOf(headers);
                String id = header(parsed, "Message-ID");
                if (id != null) {
                    return id;
                }
            } else if (content instanceof InputStream stream) {
                InternetHeaders parsed = new InternetHeaders(stream);
                String id = header(parsed, "Message-ID");
                if (id != null) {
                    return id;
                }
            }
        }
        String[] inReplyTo = message.getHeader("In-Reply-To");
        if (inReplyTo != null && inReplyTo.length > 0) {
            return inReplyTo[0];
        }
        return null;
    }

    private String extractOriginalSender(MimeMessage message, EmailPlatformProperties.Bounce limits)
            throws MessagingException, IOException {
        BodyPart rfc822 = findPart(message, "message/rfc822", 0, limits.getMaxMimeDepth());
        if (rfc822 == null) {
            rfc822 = findPart(message, "text/rfc822-headers", 0, limits.getMaxMimeDepth());
        }
        if (rfc822 == null) {
            return null;
        }
        Object content = rfc822.getContent();
        if (content instanceof MimeMessage original) {
            String[] returnPath = original.getHeader("Return-Path");
            if (returnPath != null && returnPath.length > 0) {
                return addressValue(returnPath[0], limits.getMaxHeaderLength());
            }
            String[] from = original.getHeader("From");
            if (from != null && from.length > 0) {
                return clip(from[0], limits.getMaxHeaderLength());
            }
        }
        return null;
    }

    private static int countParts(Part part, int depth, int maxDepth, int maxParts) throws MessagingException, IOException {
        if (depth > maxDepth) {
            return -1;
        }
        if (!part.isMimeType("multipart/*")) {
            return 1;
        }
        Object content = part.getContent();
        if (!(content instanceof Multipart multipart)) {
            return 1;
        }
        int total = 1;
        int count = multipart.getCount();
        for (int i = 0; i < count; i++) {
            int nested = countParts(multipart.getBodyPart(i), depth + 1, maxDepth, maxParts);
            if (nested < 0) {
                return -1;
            }
            total += nested;
            if (total > maxParts) {
                return -1;
            }
        }
        return total;
    }

    private static BodyPart findPart(Part part, String mimeType, int depth, int maxDepth)
            throws MessagingException, IOException {
        if (depth > maxDepth) {
            return null;
        }
        if (part instanceof BodyPart bodyPart && part.isMimeType(mimeType)) {
            return bodyPart;
        }
        if (!part.isMimeType("multipart/*")) {
            return null;
        }
        Object content = part.getContent();
        if (!(content instanceof Multipart multipart)) {
            return null;
        }
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart child = multipart.getBodyPart(i);
            if (child.isMimeType(mimeType)) {
                return child;
            }
            BodyPart nested = findPart(child, mimeType, depth + 1, maxDepth);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String readText(Part part, int maxBytes) throws IOException, MessagingException {
        try (InputStream stream = part.getInputStream()) {
            byte[] bytes = stream.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static List<String> splitHeaderBlocks(String body) {
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n\\s*\n");
        List<String> blocks = new ArrayList<>();
        for (String block : raw) {
            if (!block.isBlank()) {
                blocks.add(block.trim() + "\r\n");
            }
        }
        return blocks;
    }

    private static InternetHeaders headersOf(String block) throws MessagingException {
        String withCrlf = block.replace("\n", "\r\n");
        if (!withCrlf.endsWith("\r\n")) {
            withCrlf = withCrlf + "\r\n";
        }
        return new InternetHeaders(new ByteArrayInputStream((withCrlf + "\r\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static String header(InternetHeaders headers, String name) {
        String[] values = headers.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private static String firstHeader(MimeMessage message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        }
        return values[0];
    }

    private static String typedValue(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        int semi = raw.indexOf(';');
        String value = semi >= 0 ? raw.substring(semi + 1).trim() : raw.trim();
        return clip(value, maxLength);
    }

    private static String typedPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        int semi = raw.indexOf(';');
        if (semi < 0) {
            return null;
        }
        return clip(raw.substring(0, semi).trim(), 64);
    }

    private static String addressValue(String raw, int maxLength) {
        String value = typedValue(raw, maxLength);
        if (value == null) {
            return null;
        }
        return clip(value.replaceAll("[<>]", "").trim().toLowerCase(Locale.ROOT), maxLength);
    }

    private static Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Date parsed = new MailDateFormat().parse(raw.trim());
            return parsed == null ? null : parsed.toInstant();
        } catch (Exception exception) {
            return null;
        }
    }

    static String clip(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static Properties sessionProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.mime.multipart.ignoremissingendboundary", "true");
        properties.setProperty("mail.mime.ignoreunknownencoding", "true");
        properties.setProperty("mail.mime.parameters.strict", "false");
        properties.setProperty("mail.mime.allowutf8", "true");
        return properties;
    }
}
