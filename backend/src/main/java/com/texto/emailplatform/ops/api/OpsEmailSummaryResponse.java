package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OpsEmailSummaryResponse(
        UUID id,
        UUID tenantId,
        String status,
        String statusReason,
        String fromAddress,
        String recipient,
        List<String> recipientsTo,
        List<String> recipientsCc,
        List<String> recipientsBcc,
        List<String> suppressedRecipients,
        List<String> bouncedRecipients,
        String providerMessageId,
        String rfc822MessageId,
        Integer attemptCount,
        Integer maxAttempts,
        String lastError,
        Instant queuedAt,
        Instant deliveredAt,
        Instant failedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
