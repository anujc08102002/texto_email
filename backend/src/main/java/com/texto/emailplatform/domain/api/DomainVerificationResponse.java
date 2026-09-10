package com.texto.emailplatform.domain.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DomainVerificationResponse(
        UUID domainId,
        String domain,
        String status,
        String ownershipStatus,
        String spfStatus,
        String dkimStatus,
        String dmarcStatus,
        List<VerificationRecordResponse> records
) {
    public record VerificationRecordResponse(
            UUID id,
            String type,
            String name,
            String value,
            String status,
            String detail,
            String selector,
            String publicKey,
            Instant verifiedAt
    ) {
    }
}
