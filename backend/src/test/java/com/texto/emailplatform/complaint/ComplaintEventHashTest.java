package com.texto.emailplatform.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComplaintEventHashTest {

    @Test
    void providerEventIdIsPreferredAndStable() {
        String first = ComplaintEventHash.providerEvent("TEST", "evt-1");
        String second = ComplaintEventHash.providerEvent("test", "EVT-1");
        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(ComplaintEventHash.identity(new ComplaintIngestionRequest(
                "TEST",
                "evt-1",
                "<mid>",
                null,
                "alice@example.com",
                "token",
                "ABUSE",
                null,
                null
        ))).isEqualTo(first);
    }

    @Test
    void canonicalHashIgnoresTimestamps() {
        ComplaintIngestionRequest a = new ComplaintIngestionRequest(
                "INTERNAL", null, "<a@x>", null, "Alice@Example.com", "abc", "SPAM", java.time.Instant.parse("2020-01-01T00:00:00Z"), null
        );
        ComplaintIngestionRequest b = new ComplaintIngestionRequest(
                "INTERNAL", null, "<a@x>", null, "alice@example.com", "abc", "SPAM", java.time.Instant.parse("2021-01-01T00:00:00Z"), null
        );
        assertThat(ComplaintEventHash.identity(a)).isEqualTo(ComplaintEventHash.identity(b));
    }
}
