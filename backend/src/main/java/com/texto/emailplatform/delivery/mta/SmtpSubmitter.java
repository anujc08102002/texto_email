package com.texto.emailplatform.delivery.mta;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

/**
 * SMTP submission (EHLO / MAIL FROM / RCPT TO / DATA). Does not compose or alter the RFC 822 payload
 * beyond SMTP dot-stuffing required by RFC 5321.
 */
@Component
public class SmtpSubmitter {

    public MtaResult submit(SmtpEndpoint endpoint, MtaSubmitRequest request) {
        try {
            return doSubmit(endpoint, request);
        } catch (Exception exception) {
            return SmtpExceptionClassifier.classify(exception);
        }
    }

    private MtaResult doSubmit(SmtpEndpoint endpoint, MtaSubmitRequest request) throws IOException {
        SmtpEnvelope envelope = request.envelope();
        if (envelope.rcptTo().isEmpty()) {
            return SmtpExceptionClassifier.fromReply(550, "550 no recipients");
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), endpoint.connectionTimeoutMs());
        socket.setSoTimeout(Math.max(endpoint.readTimeoutMs(), endpoint.writeTimeoutMs()));
        try {
            if (endpoint.sslEnabled()) {
                socket = wrapTls(socket, endpoint.host(), endpoint.port());
            }
            Session session = Session.open(socket);
            session.expect(220);
            Ehlo ehlo = session.ehlo(endpoint.ehloHostname());
            if (!endpoint.sslEnabled() && endpoint.startTlsEnabled()) {
                if (!ehlo.startTls) {
                    if (endpoint.startTlsRequired()) {
                        return MtaResult.of(
                                MtaOutcome.TLS_FAILURE,
                                null,
                                "STARTTLS is required but was not advertised",
                                "tls_failure",
                                "STARTTLS is required but was not advertised"
                        );
                    }
                } else {
                    session.writeLine("STARTTLS");
                    session.expect(220);
                    socket = wrapTls(socket, endpoint.host(), endpoint.port());
                    session = Session.open(socket);
                    session.ehlo(endpoint.ehloHostname());
                }
            } else if (!endpoint.sslEnabled() && endpoint.startTlsRequired()) {
                return MtaResult.of(
                        MtaOutcome.TLS_FAILURE,
                        null,
                        "STARTTLS is required but disabled",
                        "tls_failure",
                        "STARTTLS is required but disabled"
                );
            }
            session.writeLine(mailFromCommand(envelope));
            session.expect(250);
            for (String recipient : envelope.rcptTo()) {
                session.writeLine("RCPT TO:<" + sanitizeAddress(recipient) + ">");
                session.expect(250);
            }
            session.writeLine("DATA");
            session.expect(354);
            writeRfc822(session, request.rfc822());
            session.writeLine(".");
            String accepted = session.expect(250);
            try {
                session.writeLine("QUIT");
            } catch (IOException ignored) {
                // Message already accepted.
            }
            return SmtpExceptionClassifier.fromReply(250, accepted);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closed after handoff.
            }
        }
    }

    private static void writeRfc822(Session session, byte[] rfc822) throws IOException {
        String text = new String(rfc822 == null ? new byte[0] : rfc822, StandardCharsets.UTF_8);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && line.isEmpty()) {
                continue;
            }
            if (line.startsWith(".")) {
                session.writeLine("." + line);
            } else {
                session.writeLine(line);
            }
        }
    }

    private static Socket wrapTls(Socket socket, String host, int port) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
        SSLParameters parameters = sslSocket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        sslSocket.setSSLParameters(parameters);
        sslSocket.startHandshake();
        return sslSocket;
    }

    static String mailFromCommand(SmtpEnvelope envelope) {
        String command = "MAIL FROM:<" + sanitizeAddress(envelope.mailFrom()) + ">";
        if (envelope.envelopeId() != null) {
            command += " ENVID=" + sanitizeEnvid(envelope.envelopeId());
        }
        return command;
    }

    private static String sanitizeAddress(String address) {
        return address == null ? "" : address.replaceAll("[\\r\\n<>]", "");
    }

    private static String sanitizeEnvid(String envelopeId) {
        return envelopeId.replaceAll("[^A-Za-z0-9+/=_-]", "");
    }

    private record Ehlo(boolean startTls) {
    }

    private static final class Session {
        private final BufferedReader in;
        private final BufferedWriter out;

        private Session(BufferedReader in, BufferedWriter out) {
            this.in = in;
            this.out = out;
        }

        static Session open(Socket socket) throws IOException {
            return new Session(
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)),
                    new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            );
        }

        void writeLine(String line) throws IOException {
            out.write(line);
            out.write("\r\n");
            out.flush();
        }

        String expect(int code) throws IOException {
            String prefix = String.valueOf(code);
            String line = in.readLine();
            if (line == null || !line.startsWith(prefix)) {
                throw new SmtpResponseException(line);
            }
            String last = line;
            while (last.length() >= 4 && last.charAt(3) == '-') {
                last = in.readLine();
                if (last == null) {
                    throw new SmtpResponseException("SMTP connection closed");
                }
            }
            return last;
        }

        Ehlo ehlo(String hostname) throws IOException {
            writeLine("EHLO " + hostname);
            boolean startTls = false;
            String line = in.readLine();
            if (line == null || !line.startsWith("250")) {
                throw new SmtpResponseException(line);
            }
            startTls = advertisesStartTls(line);
            while (line.length() >= 4 && line.charAt(3) == '-') {
                line = in.readLine();
                if (line == null) {
                    throw new SmtpResponseException("SMTP connection closed");
                }
                startTls = startTls || advertisesStartTls(line);
            }
            return new Ehlo(startTls);
        }

        private static boolean advertisesStartTls(String line) {
            return line != null && line.toUpperCase(Locale.ROOT).contains("STARTTLS");
        }
    }
}
