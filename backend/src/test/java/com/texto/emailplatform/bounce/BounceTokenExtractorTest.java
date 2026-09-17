package com.texto.emailplatform.bounce;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BounceTokenExtractorTest {

    private BounceTokenExtractor extractor;

    @BeforeEach
    void setUp() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getBounce().setDomain("bounce.texto.test");
        extractor = new BounceTokenExtractor(properties);
    }

    @Test
    void extractsTokenFromBounceAddress() {
        String token = "0123456789abcdef0123456789abcdef";
        assertThat(extractor.extract("bounce+" + token + "@bounce.texto.test")).contains(token);
        assertThat(extractor.extract("<bounce+" + token + "@bounce.texto.test>")).contains(token);
    }

    @Test
    void extractsRawEnvelopeIdToken() {
        String token = "0123456789abcdef0123456789abcdef";
        assertThat(extractor.extract(token)).contains(token);
    }

    @Test
    void rejectsWrongDomain() {
        String token = "0123456789abcdef0123456789abcdef";
        assertThat(extractor.extract("bounce+" + token + "@evil.example")).isEmpty();
        assertThat(extractor.extract("bounce+" + token + "@acme.texto.test")).isEmpty();
    }

    @Test
    void rejectsMalformedAndOversizedTokens() {
        assertThat(extractor.extract("bounce@bounce.texto.test")).isEmpty();
        assertThat(extractor.extract("bounce+not-a-token@bounce.texto.test")).isEmpty();
        assertThat(extractor.extract("bounce+" + "a".repeat(64) + "@bounce.texto.test")).isEmpty();
        assertThat(extractor.extract("x".repeat(400) + "@bounce.texto.test")).isEmpty();
        assertThat(extractor.extract("alice@example.com")).isEmpty();
    }
}
