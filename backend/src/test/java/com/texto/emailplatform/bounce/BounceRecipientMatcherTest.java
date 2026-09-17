package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.email.domain.EmailMessageEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BounceRecipientMatcherTest {

    @Test
    void matchesCaseAndWhitespaceInsensitivelyWithoutAlteringStoredValue() {
        EmailMessageEntity message = EmailMessageEntity.create(
                UUID.randomUUID(),
                "noreply@acme.texto.test",
                List.of("Alice@Example.com"),
                List.of("bob@example.com"),
                List.of(),
                List.of(),
                null,
                "Hello",
                null,
                "hello",
                null,
                null,
                null,
                Map.of(),
                5,
                null
        );
        DsnRecipient dsn = new DsnRecipient(
                "rfc822;  alice@example.com ",
                "  ALICE@EXAMPLE.COM",
                "failed",
                "5.1.1",
                null,
                null,
                null,
                null,
                null,
                BounceClassifier.classify("failed", "5.1.1", null)
        );
        assertThat(BounceRecipientMatcher.matchingStoredRecipient(message, dsn)).isEqualTo("Alice@Example.com");
        assertThat(BounceRecipientMatcher.matchingStoredRecipient(message, "not-an-address")).isNull();
        assertThat(BounceRecipientMatcher.matchingStoredRecipient(message, "carol@example.com")).isNull();
    }
}
