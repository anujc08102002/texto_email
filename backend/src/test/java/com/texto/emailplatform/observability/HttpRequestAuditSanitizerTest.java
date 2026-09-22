package com.texto.emailplatform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.texto.emailplatform.observability.domain.HttpRequestAuditEntity;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HttpRequestAuditSanitizerTest {

    @Test
    void forbidsCredentialAndEmailAuthenticationHeaders() {
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("Authorization")).isTrue();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("X-API-Key")).isTrue();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("Cookie")).isTrue();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("DKIM-Signature")).isTrue();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("Authentication-Results")).isTrue();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("Received-SPF")).isTrue();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("User-Agent")).isFalse();
        assertThat(HttpRequestAuditSanitizer.isForbiddenHeader("Referer")).isFalse();
    }

    @Test
    void redactsSecretBearingMetadataValuesThatCouldReachAuditColumns() {
        assertThat(HttpRequestAuditSanitizer.clip("Bearer token-value", 512)).isEqualTo("[redacted]");
        assertThat(HttpRequestAuditSanitizer.clip("password=secret", 512)).isEqualTo("[redacted]");
        assertThat(HttpRequestAuditSanitizer.clip("otp=123456", 512)).isEqualTo("[redacted]");
        assertThat(HttpRequestAuditSanitizer.clip("AKIAABCDEFGHIJKLMNOP", 512)).isEqualTo("[redacted]");
        assertThat(HttpRequestAuditSanitizer.clip("BEGIN RSA PRIVATE KEY", 512)).isEqualTo("[redacted]");
        assertThat(HttpRequestAuditSanitizer.clip("webhook_secret=value", 512)).isEqualTo("[redacted]");
        assertThat(HttpRequestAuditSanitizer.clip("Mozilla/5.0", 512)).isEqualTo("Mozilla/5.0");
    }

    @Test
    void persistentAuditEntityHasNoBodyOrCredentialStorageFields() {
        assertThat(Arrays.stream(HttpRequestAuditEntity.class.getDeclaredFields())
                .map(Field::getName)
                .map(String::toLowerCase)
                .toList())
                .noneMatch(name -> name.contains("body"))
                .noneMatch(name -> name.contains("authorization"))
                .noneMatch(name -> name.contains("password"))
                .noneMatch(name -> name.contains("credential"))
                .noneMatch(name -> name.contains("secret"))
                .noneMatch(name -> name.contains("token"))
                .noneMatch(name -> name.contains("headers"));
    }
}
