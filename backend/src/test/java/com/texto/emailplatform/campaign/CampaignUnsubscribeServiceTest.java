package com.texto.emailplatform.campaign;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CampaignUnsubscribeServiceTest {
    @Test void issuesUnguessableLinksAndStoresOnlyHash() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new CampaignUnsubscribeService(jdbc, "https://mail.example.com/");
        UUID tenant = UUID.randomUUID();
        String first = service.urlFor(tenant, "a@example.com");
        String second = service.urlFor(tenant, "a@example.com");
        assertThat(first).matches("https://mail.example.com/api/v1/public/unsubscribe/[a-f0-9]{64}").isNotEqualTo(second);
        String token = first.substring(first.lastIndexOf('/') + 1);
        verify(jdbc).update(startsWith("INSERT INTO campaign_unsubscribe_tokens"), eq(CampaignUnsubscribeService.hash(token)), eq(tenant), eq("a@example.com"));
        assertThat(CampaignUnsubscribeService.hash(token)).isNotEqualTo(token);
    }
    @Test void rejectsInvalidTokensBeforeDatabaseAccess() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new CampaignUnsubscribeService(jdbc, "https://mail.example.com");
        assertThatThrownBy(() -> service.unsubscribe("bad-token")).hasMessageContaining("invalid");
        verifyNoInteractions(jdbc);
    }
    @Test void scopesSubscriptionAndSuppressionToTokenTenantAndEmail() {
        var jdbc = mock(JdbcTemplate.class);
        UUID tenant = UUID.randomUUID();
        String token = "a".repeat(64);
        when(jdbc.queryForList(anyString(), eq(CampaignUnsubscribeService.hash(token))))
                .thenReturn(List.of(Map.of("tenant_id", tenant, "email", "a@example.com")));
        new CampaignUnsubscribeService(jdbc, "https://mail.example.com").unsubscribe(token);
        verify(jdbc).update(startsWith("UPDATE contacts"), eq(tenant), eq("a@example.com"));
        verify(jdbc).update(contains("ON CONFLICT (tenant_id, normalized_email) DO NOTHING"), any(UUID.class), eq(tenant), eq("a@example.com"), eq("a@example.com"));
    }
    @Test void rejectsInsecureOrAmbiguousPublicUrls() {
        for (String url : List.of("http://example.com", "https://user:password@example.com", "https://example.com?x=1", "javascript:alert(1)")) {
            assertThatThrownBy(() -> new CampaignUnsubscribeService(mock(JdbcTemplate.class), url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
