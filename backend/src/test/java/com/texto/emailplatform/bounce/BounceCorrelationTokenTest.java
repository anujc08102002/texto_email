package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BounceCorrelationTokenTest {

    @Test
    void generatesCryptographicallySizedUniqueHexTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = BounceCorrelationToken.generate();
            assertThat(BounceCorrelationToken.isWellFormed(token)).isTrue();
            assertThat(token).hasSize(32).doesNotContain(" ").doesNotContain("@");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(200);
    }

    @Test
    void fingerprintDoesNotRevealToken() {
        String token = BounceCorrelationToken.generate();
        String fingerprint = BounceCorrelationToken.fingerprint(token);
        assertThat(fingerprint).hasSize(8).isNotEqualTo(token);
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(BounceCorrelationToken.normalize("abc")).isNull();
        assertThat(BounceCorrelationToken.normalize("g".repeat(32))).isNull();
        assertThat(BounceCorrelationToken.normalize("0".repeat(31))).isNull();
        assertThat(BounceCorrelationToken.normalize("0".repeat(33))).isNull();
    }
}
