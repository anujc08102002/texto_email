package com.texto.emailplatform.domain.dns;

import java.util.Locale;
import javax.naming.CommunicationException;
import javax.naming.InvalidNameException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import javax.naming.TimeLimitExceededException;

/**
 * Maps JNDI/DNS exceptions to {@link DnsLookupOutcome} without exposing resolver details.
 */
public final class DnsErrorClassifier {

    private DnsErrorClassifier() {
    }

    public static DnsLookupOutcome classify(NamingException exception) {
        if (exception instanceof NameNotFoundException) {
            return DnsLookupOutcome.NXDOMAIN;
        }
        if (exception instanceof TimeLimitExceededException || isTimeoutMessage(exception)) {
            return DnsLookupOutcome.TIMEOUT;
        }
        if (exception instanceof InvalidNameException) {
            return DnsLookupOutcome.MALFORMED;
        }
        if (exception instanceof ServiceUnavailableException || exception instanceof CommunicationException) {
            return DnsLookupOutcome.TEMPORARY_FAILURE;
        }
        return DnsLookupOutcome.TEMPORARY_FAILURE;
    }

    public static String customerMessage(DnsLookupOutcome outcome) {
        return switch (outcome) {
            case OK -> null;
            case MISSING -> "No TXT record was found for this name.";
            case NXDOMAIN -> "This DNS name does not exist.";
            case TIMEOUT -> "DNS lookup timed out. Try again shortly.";
            case TEMPORARY_FAILURE -> "DNS lookup could not be completed. Try again shortly.";
            case MALFORMED -> "The DNS response could not be read.";
        };
    }

    private static boolean isTimeoutMessage(NamingException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("timed out") || lower.contains("timeout") || lower.contains("time-out");
    }
}
