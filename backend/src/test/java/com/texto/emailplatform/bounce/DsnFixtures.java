package com.texto.emailplatform.bounce;

import java.nio.charset.StandardCharsets;

public final class DsnFixtures {

    private DsnFixtures() {
    }

    public static byte[] hardBounce(String originalMessageId, String recipient) {
        return rfc3464(
                "failed",
                "5.1.1",
                "smtp; 550 5.1.1 User unknown",
                recipient,
                originalMessageId,
                false,
                false,
                null
        );
    }

    static byte[] softBounce(String originalMessageId, String recipient) {
        return rfc3464(
                "delayed",
                "4.2.1",
                "smtp; 450 4.2.1 Mailbox busy",
                recipient,
                originalMessageId,
                false,
                false,
                "Sun, 13 Sep 2026 06:00:00 +0000"
        );
    }

    static byte[] policyRejection(String originalMessageId, String recipient) {
        return rfc3464(
                "failed",
                "5.7.1",
                "smtp; 550 5.7.1 Policy rejection",
                recipient,
                originalMessageId,
                false,
                false,
                null
        );
    }

    static byte[] mailboxUnavailable(String originalMessageId, String recipient) {
        return rfc3464(
                "failed",
                "5.2.1",
                "smtp; 550 5.2.1 Mailbox disabled",
                recipient,
                originalMessageId,
                false,
                false,
                null
        );
    }

    static byte[] missingOptionalFields(String recipient) {
        String body = """
                From: MAILER-DAEMON@mail.example.com
                To: noreply@acme.texto.test
                Subject: Delivery Status Notification
                MIME-Version: 1.0
                Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

                --bnd
                Content-Type: text/plain; charset=utf-8

                failed

                --bnd
                Content-Type: message/delivery-status

                Reporting-MTA: dns; mail.example.com

                Final-Recipient: rfc822; %s
                Action: failed
                Status: 5.0.0

                --bnd--
                """.formatted(recipient);
        return crlf(body);
    }

    static byte[] foldedDiagnostic(String originalMessageId, String recipient) {
        String body = """
                From: MAILER-DAEMON@mail.example.com
                To: noreply@acme.texto.test
                Subject: Delivery Status Notification
                MIME-Version: 1.0
                Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

                --bnd
                Content-Type: text/plain; charset=utf-8

                failed

                --bnd
                Content-Type: message/delivery-status

                Reporting-MTA: dns; mail.example.com
                Arrival-Date: Fri, 11 Sep 2026 06:00:00 +0000

                Original-Recipient: rfc822; %s
                Final-Recipient: rfc822; %s
                Action: failed
                Status: 5.1.1
                Diagnostic-Code: smtp;
                \t550 5.1.1 User unknown

                --bnd
                Content-Type: text/rfc822-headers

                From: noreply@acme.texto.test
                Message-ID: %s

                --bnd--
                """.formatted(recipient, recipient, originalMessageId);
        return crlf(body);
    }

    static byte[] multipleRecipients(String originalMessageId) {
        String body = """
                From: MAILER-DAEMON@mail.example.com
                To: noreply@acme.texto.test
                Subject: Delivery Status Notification
                MIME-Version: 1.0
                Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

                --bnd
                Content-Type: text/plain; charset=utf-8

                failed

                --bnd
                Content-Type: message/delivery-status

                Reporting-MTA: dns; mail.example.com

                Final-Recipient: rfc822; alice@example.com
                Action: failed
                Status: 5.1.1
                Diagnostic-Code: smtp; 550 5.1.1 User unknown

                Final-Recipient: rfc822; bob@example.com
                Action: delayed
                Status: 4.2.1
                Diagnostic-Code: smtp; 450 4.2.1 try later

                --bnd
                Content-Type: text/rfc822-headers

                Message-ID: %s

                --bnd--
                """.formatted(originalMessageId);
        return crlf(body);
    }

    static byte[] withHtmlHumanPart(String originalMessageId, String recipient) {
        String body = """
                From: MAILER-DAEMON@mail.example.com
                To: noreply@acme.texto.test
                Subject: Delivery Status Notification
                MIME-Version: 1.0
                Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

                --bnd
                Content-Type: text/html; charset=utf-8

                <html><body><script>window.location='http://evil.example'</script><p>failed</p></body></html>

                --bnd
                Content-Type: message/delivery-status

                Reporting-MTA: dns; mail.example.com

                Final-Recipient: rfc822; %s
                Action: failed
                Status: 5.1.1
                Diagnostic-Code: smtp; 550 5.1.1 User unknown

                --bnd
                Content-Type: text/rfc822-headers

                Message-ID: %s

                --bnd--
                """.formatted(recipient, originalMessageId);
        return crlf(body);
    }

    static byte[] malformedStatus(String recipient) {
        return rfc3464("failed", "not-a-status", "smtp; weird", recipient, "<id@texto.local>", false, false, null);
    }

    static byte[] oversizedDiagnostic(String recipient) {
        String diagnostic = "smtp; 550 " + "x".repeat(800);
        return rfc3464("failed", "5.1.1", diagnostic, recipient, "<id@texto.local>", false, false, null);
    }

    static byte[] rfc3464(
            String action,
            String status,
            String diagnostic,
            String recipient,
            String originalMessageId,
            boolean skipArrival,
            boolean skipRemote,
            String willRetryUntil
    ) {
        String arrival = skipArrival ? "" : "Arrival-Date: Fri, 11 Sep 2026 06:00:00 +0000\n";
        String remote = skipRemote ? "" : "Remote-MTA: dns; mx.example.com\n";
        String retry = willRetryUntil == null ? "" : "Will-Retry-Until: " + willRetryUntil + "\n";
        String body = """
                From: MAILER-DAEMON@mail.example.com
                To: noreply@acme.texto.test
                Subject: Delivery Status Notification (Failure)
                MIME-Version: 1.0
                Content-Type: multipart/report; report-type=delivery-status; boundary="bnd"

                --bnd
                Content-Type: text/plain; charset=utf-8

                This is a delivery status notification.

                --bnd
                Content-Type: message/delivery-status

                Reporting-MTA: dns; mail.example.com
                %s
                Original-Recipient: rfc822; %s
                Final-Recipient: rfc822; %s
                Action: %s
                Status: %s
                %sDiagnostic-Code: %s
                %s
                --bnd
                Content-Type: text/rfc822-headers

                From: noreply@acme.texto.test
                To: %s
                Message-ID: %s
                Subject: Hello

                --bnd--
                """.formatted(
                arrival,
                recipient,
                recipient,
                action,
                status,
                remote,
                diagnostic,
                retry,
                recipient,
                originalMessageId
        );
        return crlf(body);
    }

    static byte[] crlf(String body) {
        return body.replace("\n", "\r\n").getBytes(StandardCharsets.US_ASCII);
    }
}
