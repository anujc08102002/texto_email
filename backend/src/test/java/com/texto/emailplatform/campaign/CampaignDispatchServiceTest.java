package com.texto.emailplatform.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientRepository;
import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.campaign.domain.CampaignScheduleRepository;
import com.texto.emailplatform.common.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CampaignDispatchServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private CampaignRepository campaigns;
    @Mock private CampaignRecipientRepository recipients;
    @Mock private CampaignScheduleRepository schedules;
    @Mock private CampaignRecipientSender sender;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void rateLimitLeavesRecipientPendingAndReturnsCampaignToQueue() {
        CampaignEntity campaign = queuedCampaign();
        CampaignRecipientEntity recipient = recipient(campaign);
        when(campaigns.claimForDispatch(eq(campaign.getId()), any(Instant.class), any(UUID.class), any(Instant.class))).thenAnswer(invocation -> {
            campaign.markProcessing();
            org.springframework.test.util.ReflectionTestUtils.setField(campaign, "dispatchLeaseId", invocation.getArgument(2));
            return 1;
        });
        when(campaigns.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.findByCampaignIdAndTenantIdAndStatusOrderByCreatedAtAsc(
                eq(campaign.getId()), eq(TENANT_ID), eq("PENDING"), any(Pageable.class)
        )).thenReturn(List.of(recipient));
        when(recipients.countByCampaignIdAndTenantIdAndStatus(campaign.getId(), TENANT_ID, "PENDING"))
                .thenReturn(1L);
        org.mockito.Mockito.doThrow(new ApiException(429, "EMAIL_RATE_LIMIT_EXCEEDED", "Retry later"))
                .when(sender).send(campaign.getId(), TENANT_ID, recipient.getId());

        new CampaignDispatchService(campaigns, recipients, schedules, sender, transactionManager).dispatch(campaign.getId());

        assertThat(campaign.getStatus()).isEqualTo("QUEUED");
        assertThat(recipient.getStatus()).isEqualTo("PENDING");
        verify(sender, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    void completesAfterAllRecipientsAreQueuedThroughEmailService() {
        CampaignEntity campaign = queuedCampaign();
        CampaignRecipientEntity recipient = recipient(campaign);
        when(campaigns.claimForDispatch(eq(campaign.getId()), any(Instant.class), any(UUID.class), any(Instant.class))).thenAnswer(invocation -> {
            campaign.markProcessing();
            org.springframework.test.util.ReflectionTestUtils.setField(campaign, "dispatchLeaseId", invocation.getArgument(2));
            return 1;
        });
        when(campaigns.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.findByCampaignIdAndTenantIdAndStatusOrderByCreatedAtAsc(
                eq(campaign.getId()), eq(TENANT_ID), eq("PENDING"), any(Pageable.class)
        )).thenReturn(List.of(recipient));
        when(recipients.countByCampaignIdAndTenantIdAndStatus(campaign.getId(), TENANT_ID, "PENDING"))
                .thenReturn(0L);
        when(recipients.countByCampaignIdAndTenantIdAndStatus(campaign.getId(), TENANT_ID, "FAILED"))
                .thenReturn(0L);
        when(recipients.countByCampaignIdAndTenantIdAndStatus(campaign.getId(), TENANT_ID, "ACCEPTED"))
                .thenReturn(1L);
        when(recipients.countByCampaignIdAndTenantIdAndStatus(campaign.getId(), TENANT_ID, "SUPPRESSED"))
                .thenReturn(0L);

        new CampaignDispatchService(campaigns, recipients, schedules, sender, transactionManager).dispatch(campaign.getId());

        verify(sender).send(campaign.getId(), TENANT_ID, recipient.getId());
        assertThat(campaign.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void losesClaimWithoutDispatchingAnyRecipients() {
        new CampaignDispatchService(campaigns, recipients, schedules, sender, transactionManager).dispatch(UUID.randomUUID());
        org.mockito.Mockito.verifyNoInteractions(sender, recipients, schedules);
    }

    @Test
    void stopCommittedBetweenRecipientsIsNotOverwrittenByBatchCompletion() {
        var campaign = queuedCampaign();
        var recipient = recipient(campaign);
        when(campaigns.claimForDispatch(eq(campaign.getId()), any(), any(), any())).thenAnswer(call -> {
            campaign.markProcessing();
            org.springframework.test.util.ReflectionTestUtils.setField(campaign, "dispatchLeaseId", call.getArgument(2));
            return 1;
        });
        when(campaigns.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(campaigns.lockByIdAndTenantId(campaign.getId(), TENANT_ID)).thenReturn(Optional.of(campaign));
        when(recipients.findByCampaignIdAndTenantIdAndStatusOrderByCreatedAtAsc(eq(campaign.getId()), eq(TENANT_ID), eq("PENDING"), any())).thenReturn(List.of(recipient));
        org.mockito.Mockito.doAnswer(call -> { campaign.stop(); return null; }).when(sender).send(campaign.getId(), TENANT_ID, recipient.getId());
        new CampaignDispatchService(campaigns, recipients, schedules, sender, transactionManager).dispatch(campaign.getId());
        assertThat(campaign.getStatus()).isEqualTo("STOPPED");
        verify(recipients, never()).countByCampaignIdAndTenantIdAndStatus(any(), any(), any());
    }

    private static CampaignEntity queuedCampaign() {
        CampaignEntity campaign = CampaignEntity.draft(TENANT_ID, "Test", "sender@example.com", null, "Subject", null, "<p>Hi</p>", "Hi", null, UUID.randomUUID());
        campaign.queue();
        return campaign;
    }

    private static CampaignRecipientEntity recipient(CampaignEntity campaign) {
        return CampaignRecipientEntity.queued(
                campaign.getId(), TENANT_ID, UUID.randomUUID(), "person@example.com", java.util.Map.of()
        );
    }
}