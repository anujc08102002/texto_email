package com.texto.emailplatform.domain.dns;

import java.util.List;

/**
 * One DNS TXT lookup. {@code txtRecords} contains reconstructed RDATA for each TXT
 * resource record at the owner name (RFC 1035 character-strings already concatenated).
 */
public record DnsTxtQueryResult(DnsLookupOutcome outcome, List<String> txtRecords) {

    public static DnsTxtQueryResult ok(List<String> records) {
        return new DnsTxtQueryResult(DnsLookupOutcome.OK, records == null ? List.of() : List.copyOf(records));
    }

    public static DnsTxtQueryResult missing() {
        return new DnsTxtQueryResult(DnsLookupOutcome.MISSING, List.of());
    }

    public static DnsTxtQueryResult nxdomain() {
        return new DnsTxtQueryResult(DnsLookupOutcome.NXDOMAIN, List.of());
    }

    public static DnsTxtQueryResult timeout() {
        return new DnsTxtQueryResult(DnsLookupOutcome.TIMEOUT, List.of());
    }

    public static DnsTxtQueryResult temporaryFailure() {
        return new DnsTxtQueryResult(DnsLookupOutcome.TEMPORARY_FAILURE, List.of());
    }

    public static DnsTxtQueryResult malformed() {
        return new DnsTxtQueryResult(DnsLookupOutcome.MALFORMED, List.of());
    }

    public boolean isPresent() {
        return outcome == DnsLookupOutcome.OK && txtRecords != null && !txtRecords.isEmpty();
    }
}
