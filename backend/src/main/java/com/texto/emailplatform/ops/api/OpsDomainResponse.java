package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.UUID;

public record OpsDomainResponse(
        UUID id,
        UUID tenantId,
        String domain,
        String status,
        String verificationStatus,
        String mailFromDomain,
        DnsRecordStatus spf,
        DnsRecordStatus dkim,
        DnsRecordStatus dmarc,
        DnsRecordStatus ownership,
        Instant createdAt,
        Instant updatedAt
) {

    public record DnsRecordStatus(
            String type,
            String name,
            String status,
            String selector,
            String lastError,
            Instant verifiedAt
    ) {
    }
}
