package com.texto.emailplatform.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.campaign.api.ScheduleCampaignRequest;
import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientRepository;
import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.campaign.domain.CampaignScheduleEntity;
import com.texto.emailplatform.campaign.domain.CampaignScheduleRepository;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignLifecycleServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private CampaignRepository campaigns;
    @Mock private CampaignRecipientRepository recipients;
    @Mock private CampaignScheduleRepository schedules;
    @Mock private CampaignAuthorizationService authorization;
    @Mock private CampaignReadinessService readiness;

    private CampaignLifecycleService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new CampaignLifecycleService(campaigns, recipients, schedules, authorization, readiness);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void sendsOnlyAfterAnExplicitActionAndWithRecipients() {
        CampaignEntity campaign = draft();
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.countByCampaignIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(1L);

        var response = service.sendNow(campaign.getId());

        assertThat(response.status()).isEqualTo("QUEUED");
        verify(authorization).requireSender();
    }

    @Test
    void rejectsSendWhenCampaignHasNoRecipients() {
        CampaignEntity campaign = draft();
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.countByCampaignIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.sendNow(campaign.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("recipient");
        assertThat(campaign.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void rescheduleInvalidatesTheOldActiveScheduleAndCreatesOneReplacement() {
        CampaignEntity campaign = draft();
        campaign.schedule(Instant.now().plusSeconds(1800), "UTC");
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.countByCampaignIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(1L);
        when(schedules.save(any(CampaignScheduleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant next = Instant.now().plusSeconds(3600);

        var response = service.reschedule(campaign.getId(), new ScheduleCampaignRequest(next, "Asia/Kolkata"));

        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.scheduledAt()).isEqualTo(next);
        verify(schedules).invalidateActive(campaign.getId());
        verify(schedules).save(any(CampaignScheduleEntity.class));
    }

    @Test
    void resumesStoppedCampaignBackToQueuedState() {
        CampaignEntity campaign = draft();
        campaign.schedule(Instant.now().plusSeconds(1800), "UTC");
        campaign.stop();
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.countByCampaignIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(1L);

        assertThat(service.resume(campaign.getId()).status()).isEqualTo("QUEUED");
    }

    private static CampaignEntity draft() {
        return CampaignEntity.draft(
                TENANT_ID,
                "Quarterly update",
                "updates@example.com",
                null,
                "Hello",
                null,
                "<p>Hello</p>",
                "Hello",
                null,
                UUID.randomUUID()
        );
    }
}