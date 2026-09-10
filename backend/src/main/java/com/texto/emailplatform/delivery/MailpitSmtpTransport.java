package com.texto.emailplatform.delivery;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Delivers mail to the local Mailpit SMTP sink. Production SMTP is not implemented.
 */
@Component
public class MailpitSmtpTransport implements OutboundMailTransport {

    private static final int TIMEOUT_MS = 5_000;

    private final MailpitDeliveryDestination destination;

    public MailpitSmtpTransport(MailpitDeliveryDestination destination) {
        this.destination = destination;
    }

    @Override
    public void send(OutboundMail mail) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(destination.host(), destination.smtpPort()), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expectCode(in, 220);
            writeLine(out, "EHLO texto.local");
            readEhlo(in);
            writeLine(out, "MAIL FROM:<" + sanitizeAddress(mail.from()) + ">");
            expectCode(in, 250);
            writeLine(out, "RCPT TO:<" + sanitizeAddress(mail.to()) + ">");
            expectCode(in, 250);
            writeLine(out, "DATA");
            expectCode(in, 354);
            writeLine(out, "From: " + headerValue(mail.from()));
            writeLine(out, "To: " + headerValue(mail.to()));
            writeLine(out, "Subject: " + headerValue(mail.subject()));
            writeLine(out, "MIME-Version: 1.0");
            writeLine(out, "Content-Type: text/plain; charset=UTF-8");
            writeLine(out, "Content-Transfer-Encoding: 8bit");
            writeLine(out, "");
            for (String line : mail.body().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                if (line.startsWith(".")) {
                    writeLine(out, "." + line);
                } else {
                    writeLine(out, line);
                }
            }
            writeLine(out, ".");
            expectCode(in, 250);
            writeLine(out, "QUIT");
        } catch (IOException exception) {
            throw new MailDeliveryException("Could not deliver mail through Mailpit SMTP", exception);
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
