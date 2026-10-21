package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.*;
import com.texto.emailplatform.contact.domain.*;
import com.texto.emailplatform.auth.domain.*;
import com.texto.emailplatform.email.EmailService;
import com.texto.emailplatform.template.TemplateService;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignRecipientSenderTest {
    @Test void neverQueuesAContactWhoUnsubscribedAfterAudienceWasSaved() {
        var campaigns = mock(CampaignRepository.class);
        var recipients = mock(CampaignRecipientRepository.class);
        var contacts = mock(ContactRepository.class);
        var emails = mock(EmailService.class);
        var users = mock(UserRepository.class);
        var unsubscribe = mock(CampaignUnsubscribeService.class);
        UUID tenant = UUID.randomUUID();
        var campaign = CampaignEntity.draft(tenant, "Test", "sender@example.com", null, "Hello", null, "<p>Hello</p>", null, null, UUID.randomUUID());
        campaign.queue(); campaign.markProcessing();
        var contact = ContactEntity.create(tenant, "a@example.com", "Ada", null, null, null, "manual", Map.of());
        contact.update("Ada", null, null, null, "manual", Map.of(), "UNSUBSCRIBED");
        var recipient = CampaignRecipientEntity.queued(campaign.getId(), tenant, contact.getId(), contact.getEmail(), Map.of());
        when(campaigns.lockByIdAndTenantId(campaign.getId(), tenant)).thenReturn(Optional.of(campaign));
        when(recipients.findByIdAndCampaignIdAndTenantId(recipient.getId(), campaign.getId(), tenant)).thenReturn(Optional.of(recipient));
        when(contacts.findByIdAndTenantId(contact.getId(), tenant)).thenReturn(Optional.of(contact));
        new CampaignRecipientSender(campaigns, recipients, contacts, users, emails, mock(TemplateService.class), new CampaignPersonalizer(), unsubscribe)
                .send(campaign.getId(), tenant, recipient.getId());
        assertThat(recipient.getStatus()).isEqualTo("SUPPRESSED");
        verifyNoInteractions(emails, users, unsubscribe);
    }
    @Test void stoppedCampaignNeverQueuesPendingRecipients() {
        var campaigns = mock(CampaignRepository.class);
        var recipients = mock(CampaignRecipientRepository.class);
        var emails = mock(EmailService.class);
        UUID tenant = UUID.randomUUID();
        var campaign = CampaignEntity.draft(tenant, "Test", "sender@example.com", null, "Hi", null, "<p>Hi</p>", null, null, UUID.randomUUID());
        campaign.queue(); campaign.stop();
        var recipient = CampaignRecipientEntity.queued(campaign.getId(), tenant, UUID.randomUUID(), "a@example.com", Map.of());
        when(campaigns.lockByIdAndTenantId(campaign.getId(), tenant)).thenReturn(Optional.of(campaign));
        when(recipients.findByIdAndCampaignIdAndTenantId(recipient.getId(), campaign.getId(), tenant)).thenReturn(Optional.of(recipient));
        new CampaignRecipientSender(campaigns, recipients, mock(ContactRepository.class), mock(UserRepository.class), emails, mock(TemplateService.class), new CampaignPersonalizer(), mock(CampaignUnsubscribeService.class))
                .send(campaign.getId(), tenant, recipient.getId());
        assertThat(recipient.getStatus()).isEqualTo("PENDING");
        verifyNoInteractions(emails);
    }
}
