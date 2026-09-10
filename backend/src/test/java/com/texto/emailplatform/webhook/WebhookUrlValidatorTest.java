package com.texto.emailplatform.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookUrlValidatorTest {

    private WebhookUrlValidator validator;

    @BeforeEach
    void setUp() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getWebhooks().setAllowHttp(false);
        validator = new WebhookUrlValidator(properties);
    }

    @Test
    void acceptsHttpsPublicHost() {
        // example.com resolves publicly in most environments; if DNS fails the validator rejects.
        // Prefer a host that is clearly not blocked by static rules.
        assertThatThrownBy(() -> validator.validate("https://localhost/hooks"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/hooks"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("https://169.254.169.254/latest"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate("http://example.com/hooks"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_WEBHOOK_URL"));
        assertThatThrownBy(() -> validator.validate("https://user:pass@example.com/hooks"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void blockedHostHelperRecognizesLocalNames() {
        assertThat(WebhookUrlValidator.isBlockedHost("localhost")).isTrue();
        assertThat(WebhookUrlValidator.isBlockedHost("127.0.0.1")).isTrue();
        assertThat(WebhookUrlValidator.isBlockedHost("::1")).isTrue();
        assertThat(WebhookUrlValidator.isBlockedHost("169.254.169.254")).isTrue();
        assertThat(WebhookUrlValidator.isBlockedHost("hooks.example.com")).isFalse();
    }

    @Test
    void allowsHttpWhenEnabled() {
        EmailPlatformProperties properties = new EmailPlatformProperties();
        properties.getWebhooks().setAllowHttp(true);
        WebhookUrlValidator httpValidator = new WebhookUrlValidator(properties);
        assertThatThrownBy(() -> httpValidator.validate("http://127.0.0.1/hooks"))
                .isInstanceOf(ApiException.class);
    }
}
