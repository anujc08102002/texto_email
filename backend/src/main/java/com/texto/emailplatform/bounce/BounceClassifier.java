package com.texto.emailplatform.bounce;

/**
 * Classifies RFC 3464 recipient blocks using Action + RFC 3463 status, with diagnostic
 * codes only as a fallback. Does not suppress recipients.
 */
public final class BounceClassifier {

    private BounceClassifier() {
    }

    public static BounceClassification classify(String actionField, String statusField, String diagnosticCode) {
        DsnAction action = DsnAction.fromField(actionField);
        StatusCode status = StatusCode.parse(statusField);
        BounceClass bounceClass = classifyClass(action, status, diagnosticCode);
        BounceFailureKind kind = classifyKind(status, bounceClass);
        return new BounceClassification(bounceClass, kind, action, statusField == null ? null : statusField.trim());
    }

    private static BounceClass classifyClass(DsnAction action, StatusCode status, String diagnosticCode) {
        if (action == DsnAction.DELAYED) {
            return BounceClass.SOFT_BOUNCE;
        }
        if (action == DsnAction.DELIVERED || action == DsnAction.RELAYED || action == DsnAction.EXPANDED) {
            return BounceClass.UNKNOWN;
        }
        if (action != DsnAction.FAILED) {
            return fallbackFromDiagnostic(status, diagnosticCode);
        }
        if (status != null) {
            if (status.classDigit == 5) {
                return BounceClass.HARD_BOUNCE;
            }
            if (status.classDigit == 4) {
                return BounceClass.SOFT_BOUNCE;
            }
            return BounceClass.UNKNOWN;
        }
        return fallbackFromDiagnostic(null, diagnosticCode);
    }

    private static BounceClass fallbackFromDiagnostic(StatusCode status, String diagnosticCode) {
        if (status != null) {
            if (status.classDigit == 5) {
                return BounceClass.HARD_BOUNCE;
            }
            if (status.classDigit == 4) {
                return BounceClass.SOFT_BOUNCE;
            }
        }
        Integer smtp = smtpReplyClass(diagnosticCode);
        if (smtp != null && smtp == 5) {
            return BounceClass.HARD_BOUNCE;
        }
        if (smtp != null && smtp == 4) {
            return BounceClass.SOFT_BOUNCE;
        }
        return BounceClass.UNKNOWN;
    }

    private static BounceFailureKind classifyKind(StatusCode status, BounceClass bounceClass) {
        if (status != null) {
            return switch (status.subject) {
                case 1 -> BounceFailureKind.ADDRESS_RELATED;
                case 2 -> BounceFailureKind.MAILBOX_UNAVAILABLE;
                case 7 -> BounceFailureKind.POLICY_REJECTION;
                default -> defaultKind(bounceClass);
            };
        }
        return defaultKind(bounceClass);
    }

    private static BounceFailureKind defaultKind(BounceClass bounceClass) {
        return switch (bounceClass) {
            case HARD_BOUNCE -> BounceFailureKind.PERMANENT_RECIPIENT;
            case SOFT_BOUNCE -> BounceFailureKind.TEMPORARY_RECIPIENT;
            case UNKNOWN -> BounceFailureKind.UNKNOWN;
        };
    }

    private static Integer smtpReplyClass(String diagnosticCode) {
        if (diagnosticCode == null) {
            return null;
        }
        for (int i = 0; i < diagnosticCode.length() - 2; i++) {
            char a = diagnosticCode.charAt(i);
            char b = diagnosticCode.charAt(i + 1);
            char c = diagnosticCode.charAt(i + 2);
            if (Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c)
                    && (a == '4' || a == '5') && i + 3 < diagnosticCode.length()) {
                char next = diagnosticCode.charAt(i + 3);
                if (next == ' ' || next == '-') {
                    return a - '0';
                }
            }
        }
        return null;
    }

    /**
     * RFC 3463 {@code class.subject.detail}. Null when the status field is absent or malformed.
     */
    record StatusCode(int classDigit, int subject, int detail) {
        static StatusCode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String token = raw.trim();
            int paren = token.indexOf('(');
            if (paren > 0) {
                token = token.substring(0, paren).trim();
            }
            String[] parts = token.split("\\.");
            if (parts.length < 3) {
                return null;
            }
            try {
                int classDigit = Integer.parseInt(parts[0]);
                int subject = Integer.parseInt(parts[1]);
                int detail = Integer.parseInt(parts[2].replaceAll("[^0-9].*$", ""));
                if (classDigit < 2 || classDigit > 5) {
                    return null;
                }
                return new StatusCode(classDigit, subject, detail);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
