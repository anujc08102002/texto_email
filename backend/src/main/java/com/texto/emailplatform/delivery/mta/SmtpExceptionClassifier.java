package com.texto.emailplatform.delivery.mta;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;

/**
 * Maps Jakarta Mail / I/O failures to {@link MtaResult}. Does not decide retries for the
 * delivery state machine beyond the {@code retryable} flag on the result.
 */
public final class SmtpExceptionClassifier {

    private SmtpExceptionClassifier() {
    }

    public static MtaResult classify(Exception exception) {
        Integer smtpCode = smtpCode(exception);
        String enhanced = enhancedStatus(exception);
        String diagnostic = diagnostic(exception);

        if (isTimeout(exception)) {
            return MtaResult.failure(MtaOutcome.TIMEOUT, true, codeString(smtpCode), enhanced, diagnostic);
        }
        if (isTls(exception)) {
            return MtaResult.failure(MtaOutcome.TLS_FAILURE, false, codeString(smtpCode), enhanced, diagnostic);
        }
        if (isConnection(exception)) {
            return MtaResult.failure(MtaOutcome.CONNECTION_FAILURE, true, codeString(smtpCode), enhanced, diagnostic);
        }
        if (smtpCode != null) {
            int clazz = smtpCode / 100;
            if (clazz == 4) {
                return MtaResult.failure(MtaOutcome.TEMPORARY_FAILURE, true, String.valueOf(smtpCode), enhanced, diagnostic);
            }
            if (clazz == 5) {
                return MtaResult.failure(MtaOutcome.PERMANENT_FAILURE, false, String.valueOf(smtpCode), enhanced, diagnostic);
            }
            if (clazz == 2) {
                return MtaResult.success(String.valueOf(smtpCode), diagnostic, null);
            }
        }
        return MtaResult.failure(MtaOutcome.UNKNOWN_FAILURE, true, codeString(smtpCode), enhanced, diagnostic);
    }

    static Integer smtpCode(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SMTPSendFailedException sendFailed) {
                return sendFailed.getReturnCode();
            }
            if (current instanceof SMTPAddressFailedException addressFailed) {
                return addressFailed.getReturnCode();
            }
            Integer parsed = parseLeadingCode(current.getMessage());
            if (parsed != null) {
                return parsed;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String enhancedStatus(Throwable exception) {
        String message = diagnostic(exception);
        if (message == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b([245]\\.\\d+\\.\\d+)\\b").matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static boolean isTimeout(Throwable exception) {
        return contains(exception, SocketTimeoutException.class)
                || contains(exception, TimeoutException.class)
                || messageContains(exception, "timed out", "timeout");
    }

    private static boolean isTls(Throwable exception) {
        return contains(exception, SSLHandshakeException.class)
                || contains(exception, SSLException.class)
                || contains(exception, CertificateException.class)
                || messageContains(exception, "starttls", "sslhandshake", "pkix", "certificate");
    }

    private static boolean isConnection(Throwable exception) {
        return contains(exception, ConnectException.class)
                || contains(exception, UnknownHostException.class)
                || contains(exception, NoRouteToHostException.class)
                || contains(exception, AuthenticationFailedException.class)
                || (contains(exception, SocketException.class) && !isTimeout(exception))
                || messageContains(exception, "connection refused", "unreachable", "could not connect");
    }

    private static boolean contains(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean messageContains(Throwable exception, String... needles) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                for (String needle : needles) {
                    if (lower.contains(needle)) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static Integer parseLeadingCode(String message) {
        if (message == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|\\s)([245]\\d\\d)\\b").matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private static String diagnostic(Throwable exception) {
        if (exception == null) {
            return "SMTP failure";
        }
        if (exception instanceof SendFailedException || exception instanceof MessagingException) {
            return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private static String codeString(Integer code) {
        return code == null ? null : String.valueOf(code);
    }
}
