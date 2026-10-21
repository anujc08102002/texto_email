package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.template.TemplateService;
import com.texto.emailplatform.template.domain.TemplateVersionEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignReadinessServiceTest {
    @Test void rejectsIncompleteDraftBeforeItCanBeQueued() {
        var campaign = CampaignEntity.draft(UUID.randomUUID(), "Test", null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> new CampaignReadinessService(mock(DomainService.class), mock(TemplateService.class)).validate(campaign))
                .hasMessageContaining("sender");
        assertThat(campaign.getStatus()).isEqualTo("DRAFT");
    }
    @Test void pinsTemplateVersionWhenCampaignBecomesSendable() {
        UUID tenant = UUID.randomUUID(), template = UUID.randomUUID();
        var campaign = CampaignEntity.draft(tenant, "Test", "sender@example.test", null, null, null, null, null, template, null);
        var templates = mock(TemplateService.class); var version = mock(TemplateVersionEntity.class);
        when(templates.requireRenderableVersion(tenant, template, null)).thenReturn(version);
        when(version.getVersion()).thenReturn(3);
        new CampaignReadinessService(mock(DomainService.class), templates).validate(campaign);
        assertThat(campaign.getTemplateVersion()).isEqualTo(3);
    }
    @Test void validatesIanaTimezone() {
        assertThatCode(() -> CampaignReadinessService.validateTimezone("Asia/Kolkata")).doesNotThrowAnyException();
        assertThatThrownBy(() -> CampaignReadinessService.validateTimezone("Not/AZone")).hasMessageContaining("timezone");
    }
}
