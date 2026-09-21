package com.texto.emailplatform.bounce;

import com.texto.emailplatform.email.domain.EmailMessageEntity;
import com.texto.emailplatform.suppression.EmailNormalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches a DSN recipient onto stored outbound recipients using {@link EmailNormalizer}.
 */
public final class BounceRecipientMatcher {

    private BounceRecipientMatcher() {
    }

    public static String matchingStoredRecipient(EmailMessageEntity message, DsnRecipient dsnRecipient) {
        if (message == null || dsnRecipient == null) {
            return null;
        }
        String candidate = dsnRecipient.finalRecipient() != null
                ? dsnRecipient.finalRecipient()
                : dsnRecipient.originalRecipient();
        return matchingStoredRecipient(message, candidate);
    }

    public static String matchingStoredRecipient(EmailMessageEntity message, String rawAddress) {
        String want = EmailNormalizer.normalize(rawAddress);
        if (want.isEmpty() || !want.contains("@")) {
            return null;
        }
        for (String stored : deliverableRecipients(message)) {
            if (want.equals(EmailNormalizer.normalize(stored))) {
                return stored;
            }
        }
        return null;
    }

    /**
     * TO, CC, or BCC for a stored address already known to belong to the message.
     */
    public static String recipientType(EmailMessageEntity message, String storedRecipient) {
        String want = EmailNormalizer.normalize(storedRecipient);
        if (want.isEmpty() || message == null) {
            return null;
        }
        if (containsNormalized(message.getRecipientsTo(), want)) {
            return "TO";
        }
        if (containsNormalized(message.getRecipientsCc(), want)) {
            return "CC";
        }
        if (containsNormalized(message.getRecipientsBcc(), want)) {
            return "BCC";
        }
        return null;
    }

    private static boolean containsNormalized(List<String> stored, String want) {
        if (stored == null) {
            return false;
        }
        for (String value : stored) {
            if (want.equals(EmailNormalizer.normalize(value))) {
                return true;
            }
        }
        return false;
    }

    public static List<String> deliverableRecipients(EmailMessageEntity message) {
        List<String> recipients = new ArrayList<>();
        addAll(recipients, message.getRecipientsTo());
        addAll(recipients, message.getRecipientsCc());
        addAll(recipients, message.getRecipientsBcc());
        return recipients;
    }

    private static void addAll(List<String> target, List<String> source) {
        if (source == null) {
            return;
        }
        target.addAll(source);
    }
}
