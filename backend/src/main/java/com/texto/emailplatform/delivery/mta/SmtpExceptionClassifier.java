package com.texto.emailplatform.delivery.mta;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/**
 * Maps SMTP replies and transport failures onto {@link MtaResult}. Shared by all MTA clients.
 */
public final class SmtpExceptionClassifier {

    private SmtpExceptionClassifier() {
    }

    public static MtaResult fromReply(int code, String response) {
        String smtpCode = code > 0 ? String.valueOf(code) : null;
        String text = response == null ? "" : response;
        if (code >= 200 && code < 300) {
            return MtaResult.success(smtpCode, text, extractQueueId(text));
        }
        if (code >= 400 && code < 500) {
            return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, smtpCode, text, "smtp_" + code, text);
        }
        if (code >= 500 && code < 600) {
            return MtaResult.of(MtaOutcome.PERMANENT_FAILURE, smtpCode, text, "smtp_" + code, text);
        }
        return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, smtpCode, text, "smtp_unexpected", text.isBlank() ? "Unexpected SMTP reply" : text);
    }

    public static MtaResult classify(Exception exception) {
        if (exception instanceof SmtpResponseException smtp) {
            if (smtp.code() > 0) {
                return fromReply(smtp.code(), smtp.response());
            }
            return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, null, smtp.response(), "smtp_unexpected", smtp.response());
        }
        if (exception instanceof SocketTimeoutException) {
            return MtaResult.of(MtaOutcome.TIMEOUT, null, exception.getMessage(), "timeout", message(exception, "SMTP timeout"));
        }
        if (exception instanceof SSLException) {
            return MtaResult.of(MtaOutcome.TLS_FAILURE, null, exception.getMessage(), "tls_failure", message(exception, "TLS negotiation failed"));
        }
        if (exception instanceof ConnectException
                || exception instanceof UnknownHostException
                || exception instanceof NoRouteToHostException
                || exception instanceof PortUnreachableException) {
            return MtaResult.of(MtaOutcome.CONNECTION_FAILURE, null, exception.getMessage(), "connection_failure", message(exception, "SMTP connection failed"));
        }
        if (exception instanceof IOException io) {
            int embedded = SmtpResponseException.parseCode(io.getMessage());
            if (embedded >= 200) {
                return fromReply(embedded, io.getMessage());
            }
            return MtaResult.of(MtaOutcome.CONNECTION_FAILURE, null, io.getMessage(), "connection_failure", message(io, "SMTP I/O failure"));
        }
        return MtaResult.of(MtaOutcome.TEMPORARY_FAILURE, null, exception.getMessage(), "mta_error", message(exception, "MTA submission failed"));
    }

    static String extractQueueId(String response) {
        if (response == null) {
            return null;
        }
        int queuedAs = response.toLowerCase().indexOf("queued as ");
        if (queuedAs >= 0) {
            String rest = response.substring(queuedAs + "queued as ".length()).trim();
            int space = rest.indexOf(' ');
            return space < 0 ? rest : rest.substring(0, space);
        }
        return null;
    }

    private static String message(Exception exception, String fallback) {
        String text = exception.getMessage();
        return text == null || text.isBlank() ? fallback : text;
    }
}
