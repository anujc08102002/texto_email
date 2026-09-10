package com.texto.emailplatform.delivery;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Mailpit-backed {@link DeliveryEngine} used by the async delivery worker.
 * Keeps {@link MailpitSmtpTransport} for backward-compatible single-recipient sends.
 */
@Component
@Primary
public class MailpitDeliveryEngine implements DeliveryEngine {

    private static final int TIMEOUT_MS = 5_000;

    private final MailpitDeliveryDestination destination;

    public MailpitDeliveryEngine(MailpitDeliveryDestination destination) {
        this.destination = destination;
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
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

            List<String> recipients = allRecipients(request);
            if (recipients.isEmpty()) {
                return DeliveryResult.permanent("no_recipients", "No deliverable recipients", "550");
            }
            for (String recipient : recipients) {
                writeLine(out, "RCPT TO:<" + sanitizeAddress(recipient) + ">");
                expectCode(in, 250);
            }

            writeLine(out, "DATA");
            expectCode(in, 354);
            writeHeadersAndBody(out, request);
            writeLine(out, ".");
            expectCode(in, 250);
            writeLine(out, "QUIT");

            String providerMessageId = "<" + UUID.randomUUID() + "@texto.local>";
            return DeliveryResult.success(providerMessageId, "250 Ok");
        } catch (IOException exception) {
            return classify(exception);
        }
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

    private static void writeHeadersAndBody(BufferedWriter out, DeliveryRequest request) throws IOException {
        writeLine(out, "From: " + headerValue(request.from()));
        writeLine(out, "To: " + headerValue(String.join(", ", nullToEmpty(request.to()))));
        if (request.cc() != null && !request.cc().isEmpty()) {
            writeLine(out, "Cc: " + headerValue(String.join(", ", request.cc())));
        }
        if (request.replyTo() != null && !request.replyTo().isBlank()) {
            writeLine(out, "Reply-To: " + headerValue(request.replyTo()));
        }
        writeLine(out, "Subject: " + headerValue(request.subject()));
        writeLine(out, "MIME-Version: 1.0");

        boolean hasHtml = request.htmlBody() != null && !request.htmlBody().isBlank();
        boolean hasText = request.textBody() != null && !request.textBody().isBlank();
        if (hasHtml && hasText) {
            String boundary = "texto-" + UUID.randomUUID();
            writeLine(out, "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"");
            writeLine(out, "");
            writeLine(out, "--" + boundary);
            writeLine(out, "Content-Type: text/plain; charset=UTF-8");
            writeLine(out, "Content-Transfer-Encoding: 8bit");
            writeLine(out, "");
            writeBody(out, request.textBody());
            writeLine(out, "--" + boundary);
            writeLine(out, "Content-Type: text/html; charset=UTF-8");
            writeLine(out, "Content-Transfer-Encoding: 8bit");
            writeLine(out, "");
            writeBody(out, request.htmlBody());
            writeLine(out, "--" + boundary + "--");
        } else if (hasHtml) {
            writeLine(out, "Content-Type: text/html; charset=UTF-8");
            writeLine(out, "Content-Transfer-Encoding: 8bit");
            writeLine(out, "");
            writeBody(out, request.htmlBody());
        } else {
            writeLine(out, "Content-Type: text/plain; charset=UTF-8");
            writeLine(out, "Content-Transfer-Encoding: 8bit");
            writeLine(out, "");
            writeBody(out, hasText ? request.textBody() : "");
        }
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static void writeBody(BufferedWriter out, String body) throws IOException {
        for (String line : body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (line.startsWith(".")) {
                writeLine(out, "." + line);
            } else {
                writeLine(out, line);
            }
        }
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
        return value.replaceAll("[\\r\\n]", " ");
    }
}
