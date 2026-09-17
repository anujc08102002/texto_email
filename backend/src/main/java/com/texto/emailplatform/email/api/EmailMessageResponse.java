package com.texto.emailplatform.email.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EmailMessageResponse(
        UUID id,
        String status,
        String recipient,
        String fromAddress,
        String replyTo,
        String subject,
        String statusReason,
        List<String> recipientsTo,
        List<String> recipientsCc,
        List<String> recipientsBcc,
        List<String> suppressedRecipients,
        List<String> bouncedRecipients,
        List<String> softBouncedRecipients,
        Map<String, Object> metadata,
        String providerMessageId,
        Integer attemptCount,
        Integer maxAttempts,
        Instant nextAttemptAt,
        Instant queuedAt,
        Instant deliveredAt,
        Instant failedAt,
        String lastError,
        UUID templateId,
        UUID templateVersionId,
        Instant createdAt
) {
}
