package com.texto.emailplatform.delivery;

import com.texto.emailplatform.domain.DomainNormalizer;
import com.texto.emailplatform.domain.dkim.DkimKeyService;
import com.texto.emailplatform.domain.dkim.DkimSignableMessage;
import com.texto.emailplatform.domain.dkim.DkimSigner;
import com.texto.emailplatform.domain.dkim.DkimSigningKey;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Mailpit-backed {@link DeliveryEngine} used by the async delivery worker.
 *
 * <p>The message headers and body are fully finalized before transmission so that, when the sender
 * domain has an active DKIM key, a valid {@code DKIM-Signature} can be computed over the exact bytes
 * that are sent. No signed content is mutated after signing. Mailpit remains the destination —
 * production SMTP is not implemented here.
 */
@Component
@Primary
public class MailpitDeliveryEngine implements DeliveryEngine {

    private static final int TIMEOUT_MS = 5_000;
    private static final DateTimeFormatter RFC5322_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    private final MailpitDeliveryDestination destination;
    private final DkimSigner dkimSigner;
    private final DkimKeyService dkimKeyService;

    public MailpitDeliveryEngine(
            MailpitDeliveryDestination destination,
            DkimSigner dkimSigner,
            DkimKeyService dkimKeyService
    ) {
        this.destination = destination;
        this.dkimSigner = dkimSigner;
        this.dkimKeyService = dkimKeyService;
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        List<String> recipients = allRecipients(request);
        if (recipients.isEmpty()) {
            return DeliveryResult.permanent("no_recipients", "No deliverable recipients", "550");
        }

        // Finalize the message and sign BEFORE opening the socket. A deterministic signing failure
        // must be a delivery failure, not a silently-unsigned send.
        FinalizedMessage message;
        try {
            message = composeAndSign(request);
        } catch (RuntimeException exception) {
            // Deterministic (config/crypto) error — permanent so retries do not loop forever.
            return DeliveryResult.permanent("dkim_signing_failed", "DKIM signing failed", null);
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(destination.host(), destination.smtpPort()), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expectCode(in, 220);
            writeLine(out, "EHLO texto.local");
            readEhlo(in);
            writeLine(out, "MAIL FROM:<" + sanitizeAddress(request.from()) + ">");
            expectCode(in, 250);
            for (String recipient : recipients) {
                writeLine(out, "RCPT TO:<" + sanitizeAddress(recipient) + ">");
                expectCode(in, 250);
            }

            writeLine(out, "DATA");
            expectCode(in, 354);
            writeDataPayload(out, message.wireFormat());
            writeLine(out, ".");
            expectCode(in, 250);
            writeLine(out, "QUIT");

            return DeliveryResult.success(message.messageId(), "250 Ok");
        } catch (IOException exception) {
            return classify(exception);
        }
    }

    // ---- Composition + signing ----------------------------------------------------------------

    private FinalizedMessage composeAndSign(DeliveryRequest request) {
        String senderDomain = DomainNormalizer.normalize(hostOf(request.from()));
        String messageId = "<" + UUID.randomUUID() + "@" + (senderDomain.isBlank() ? "texto.local" : senderDomain) + ">";

        List<DkimSignableMessage.Header> headers = new ArrayList<>();
        headers.add(header("From", request.from()));
        headers.add(header("To", String.join(", ", nullToEmpty(request.to()))));
        if (request.cc() != null && !request.cc().isEmpty()) {
            headers.add(header("Cc", String.join(", ", request.cc())));
        }
        if (request.replyTo() != null && !request.replyTo().isBlank()) {
            headers.add(header("Reply-To", request.replyTo()));
        }
        headers.add(header("Subject", request.subject()));
        headers.add(header("Date", OffsetDateTime.now(ZoneOffset.UTC).format(RFC5322_DATE)));
        headers.add(header("Message-ID", messageId));
        headers.add(header("MIME-Version", "1.0"));

        BodyPart bodyPart = buildBody(request);
        headers.add(header("Content-Type", bodyPart.contentType()));

        String body = bodyPart.body();

        // Resolve the tenant's active signing key for this verified sender domain (if any). Platform
        // test senders (*.texto.test) and unverified domains resolve to empty and are sent unsigned.
        Optional<DkimSigningKey> signingKey =
                dkimKeyService.findActiveSigningKey(request.tenantId(), senderDomain);

        List<DkimSignableMessage.Header> finalHeaders = new ArrayList<>();
        signingKey.ifPresent(key -> {
            String dkimValue = dkimSigner.sign(
                    new DkimSignableMessage(headers, body),
                    key.privateKey(),
                    senderDomain,
                    key.selector()
            );
            finalHeaders.add(header("DKIM-Signature", dkimValue));
        });
        finalHeaders.addAll(headers);

        return new FinalizedMessage(finalHeaders, body, messageId);
    }

    private record BodyPart(String contentType, String body) {
    }

    private static BodyPart buildBody(DeliveryRequest request) {
        boolean hasHtml = request.htmlBody() != null && !request.htmlBody().isBlank();
        boolean hasText = request.textBody() != null && !request.textBody().isBlank();
        StringBuilder body = new StringBuilder();
        if (hasHtml && hasText) {
            String boundary = "texto-" + UUID.randomUUID();
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
            return new BodyPart("multipart/alternative; boundary=\"" + boundary + "\"", body.toString());
        }
        if (hasHtml) {
            appendBody(body, request.htmlBody());
            return new BodyPart("text/html; charset=UTF-8", body.toString());
        }
        appendBody(body, hasText ? request.textBody() : "");
        return new BodyPart("text/plain; charset=UTF-8", body.toString());
    }

    /** Serializes finalized headers + body into the exact CRLF wire representation (pre dot-stuffing). */
    private record FinalizedMessage(List<DkimSignableMessage.Header> headers, String body, String messageId) {
        String wireFormat() {
            StringBuilder message = new StringBuilder();
            for (DkimSignableMessage.Header header : headers) {
                // DKIM-Signature values already contain folding CRLF+WSP; other values are single-line.
                message.append(header.name()).append(": ").append(header.value()).append("\r\n");
            }
            message.append("\r\n");
            message.append(body);
            return message.toString();
        }
    }

    private static void writeDataPayload(BufferedWriter out, String payload) throws IOException {
        String normalized = payload.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        // Avoid emitting a spurious trailing empty line for bodies that already end with CRLF.
        int end = lines.length;
        if (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        for (int i = 0; i < end; i++) {
            String line = lines[i];
            if (line.startsWith(".")) {
                writeLine(out, "." + line);
            } else {
                writeLine(out, line);
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static DkimSignableMessage.Header header(String name, String value) {
        return new DkimSignableMessage.Header(name, headerValue(value));
    }

    private static DeliveryResult classify(IOException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("550")) {
            return DeliveryResult.permanent("smtp_550", message, "550");
        }
        if (message.contains("421") || message.contains("450") || message.contains("451")) {
            String code = message.contains("421") ? "421" : message.contains("450") ? "450" : "451";
            return DeliveryResult.temporary("smtp_" + code, message, code);
        }
        return DeliveryResult.temporary("io_error", message.isBlank() ? "SMTP I/O failure" : message, null);
    }

    private static List<String> allRecipients(DeliveryRequest request) {
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

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String hostOf(String address) {
        if (address == null || !address.contains("@")) {
            return "";
        }
        return address.substring(address.lastIndexOf('@') + 1).trim();
    }

    private static void appendBody(StringBuilder out, String body) {
        for (String line : body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            out.append(line).append("\r\n");
        }
    }

    private static void appendLine(StringBuilder out, String line) {
        out.append(line).append("\r\n");
    }

    private static void writeLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private static void expectCode(BufferedReader in, int code) throws IOException {
        String prefix = String.valueOf(code);
        String line = in.readLine();
        if (line == null || !line.startsWith(prefix)) {
            throw new IOException("Unexpected SMTP response: " + line);
        }
        while (line.length() >= 4 && line.charAt(3) == '-') {
            line = in.readLine();
            if (line == null) {
                throw new IOException("SMTP connection closed");
            }
        }
    }

    private static void readEhlo(BufferedReader in) throws IOException {
        expectCode(in, 250);
    }

    private static String sanitizeAddress(String address) {
        return address.replaceAll("[\\r\\n<>]", "");
    }

    private static String headerValue(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]", " ");
    }
}
