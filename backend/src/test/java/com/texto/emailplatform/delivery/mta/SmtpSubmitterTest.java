package com.texto.emailplatform.delivery.mta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SmtpSubmitterTest {

    @Test
    void successFourXxAndFiveXxAreClassified() throws Exception {
        assertThat(runAgainstStub(250, "250 Ok: queued as Q1").outcome()).isEqualTo(MtaOutcome.SUCCESS);
        assertThat(runAgainstStub(451, "451 try later").outcome()).isEqualTo(MtaOutcome.TEMPORARY_FAILURE);
        assertThat(runAgainstStub(550, "550 no").outcome()).isEqualTo(MtaOutcome.PERMANENT_FAILURE);
    }

    @Test
    void connectionFailureWhenNothingListens() {
        SmtpEndpoint endpoint = new SmtpEndpoint(
                "127.0.0.1",
                1,
                250,
                250,
                250,
                "texto.local",
                false,
                false,
                false
        );
        MtaResult result = new SmtpSubmitter().submit(endpoint, request());
        assertThat(result.outcome()).isEqualTo(MtaOutcome.CONNECTION_FAILURE);
    }

    @Test
    void doesNotAlterRfc822Payload() throws Exception {
        String payload = "From: a@texto.test\r\nTo: alice@texto.test\r\nSubject: x\r\nDKIM-Signature: v=1; b=abc\r\n\r\nhello\r\n";
        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread.startVirtualThread(() -> serve(server, 250, "250 Ok: queued as KEEP", received, done));
            SmtpEndpoint endpoint = endpoint(server.getLocalPort());
            MtaSubmitRequest submit = new MtaSubmitRequest(
                    new SmtpEnvelope("a@texto.test", List.of("alice@texto.test")),
                    payload.getBytes(StandardCharsets.UTF_8)
            );
            MtaResult result = new SmtpSubmitter().submit(endpoint, submit);
            assertThat(result.outcome()).isEqualTo(MtaOutcome.SUCCESS);
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).contains("DKIM-Signature: v=1; b=abc");
            assertThat(received.get()).contains("hello");
        }
    }

    private static MtaResult runAgainstStub(int finalCode, String finalLine) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch done = new CountDownLatch(1);
            Thread.startVirtualThread(() -> serve(server, finalCode, finalLine, new AtomicReference<>(), done));
            MtaResult result = new SmtpSubmitter().submit(endpoint(server.getLocalPort()), request());
            done.await(5, TimeUnit.SECONDS);
            return result;
        }
    }

    private static SmtpEndpoint endpoint(int port) {
        return new SmtpEndpoint("127.0.0.1", port, 2000, 2000, 2000, "texto.local", false, false, false);
    }

    private static MtaSubmitRequest request() {
        return new MtaSubmitRequest(
                new SmtpEnvelope("noreply@acme.texto.test", List.of("alice@texto.test")),
                "From: noreply@acme.texto.test\r\nTo: alice@texto.test\r\nSubject: t\r\n\r\nbody\r\n"
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void serve(
            ServerSocket server,
            int finalCode,
            String finalLine,
            AtomicReference<String> data,
            CountDownLatch done
    ) {
        try (Socket socket = server.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            write(out, "220 mail.texto.test ESMTP");
            readCommand(in);
            write(out, "250-localhost");
            write(out, "250 OK");
            readCommand(in);
            write(out, "250 OK");
            readCommand(in);
            write(out, "250 OK");
            readCommand(in);
            write(out, "354 End data with <CR><LF>.<CR><LF>");
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (".".equals(line)) {
                    break;
                }
                body.append(line).append("\r\n");
            }
            data.set(body.toString());
            write(out, finalLine.startsWith(String.valueOf(finalCode)) ? finalLine : finalCode + " " + finalLine);
            in.readLine();
        } catch (Exception ignored) {
            // test socket
        } finally {
            done.countDown();
        }
    }

    private static void write(BufferedWriter out, String line) throws Exception {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    private static void readCommand(BufferedReader in) throws Exception {
        in.readLine();
    }
}
