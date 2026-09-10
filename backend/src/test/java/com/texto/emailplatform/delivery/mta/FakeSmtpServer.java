package com.texto.emailplatform.delivery.mta;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only SMTP listener. Not a production MTA.
 */
final class FakeSmtpServer implements AutoCloseable {

    enum Mode {
        SUCCESS,
        TEMPORARY_DATA,
        PERMANENT_RCPT,
        HANG_BANNER
    }

    private final Mode mode;
    private final ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicInteger closedConnections = new AtomicInteger();
    private final AtomicReference<String> lastData = new AtomicReference<>();
    private final Thread thread;

    FakeSmtpServer(Mode mode) throws IOException {
        this.mode = mode;
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        this.thread = new Thread(this::serve, "fake-smtp");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    String lastData() {
        return lastData.get();
    }

    int connections() {
        return connections.get();
    }

    int closedConnections() {
        return closedConnections.get();
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        serverSocket.close();
        thread.interrupt();
    }

    private void serve() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                connections.incrementAndGet();
                try {
                    handle(socket);
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                    closedConnections.incrementAndGet();
                }
            } catch (IOException exception) {
                if (running.get()) {
                    return;
                }
            }
        }
    }

    private void handle(Socket socket) throws IOException {
        socket.setSoTimeout(5_000);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        if (mode == Mode.HANG_BANNER) {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        write(out, "220 fake.smtp");
        String line;
        while ((line = in.readLine()) != null) {
            String upper = line.toUpperCase();
            if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                write(out, "250-fake.smtp");
                write(out, "250 8BITMIME");
            } else if (upper.startsWith("MAIL FROM:")) {
                write(out, "250 OK");
            } else if (upper.startsWith("RCPT TO:")) {
                if (mode == Mode.PERMANENT_RCPT) {
                    write(out, "550 5.1.1 mailbox unavailable");
                } else {
                    write(out, "250 OK");
                }
            } else if (upper.equals("DATA")) {
                if (mode == Mode.TEMPORARY_DATA) {
                    write(out, "450 4.7.1 try again later");
                    continue;
                }
                write(out, "354 Start mail input");
                StringBuilder data = new StringBuilder();
                String dataLine;
                while ((dataLine = in.readLine()) != null) {
                    if (".".equals(dataLine)) {
                        break;
                    }
                    data.append(dataLine).append("\r\n");
                }
                lastData.set(data.toString());
                write(out, "250 2.0.0 OK");
            } else if (upper.equals("QUIT")) {
                write(out, "221 Bye");
                return;
            } else {
                write(out, "250 OK");
            }
        }
    }

    private static void write(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }
}
