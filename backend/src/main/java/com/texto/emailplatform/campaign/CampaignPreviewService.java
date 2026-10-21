package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.*;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.email.EmailService;
import com.texto.emailplatform.email.api.EmailMessageResponse;
import com.texto.emailplatform.email.api.SendEmailRequest;
import com.texto.emailplatform.template.TemplateService;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignPreviewService {
    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final CampaignPersonalizer personalizer;
    private final TemplateService templates;
    private final EmailService emails;
    private final CampaignAuthorizationService authorization;

    public CampaignPreviewService(CampaignRepository campaigns, CampaignRecipientRepository recipients,
            CampaignPersonalizer personalizer, TemplateService templates, EmailService emails, CampaignAuthorizationService authorization) {
        this.campaigns = campaigns; this.recipients = recipients; this.personalizer = personalizer;
        this.templates = templates; this.emails = emails; this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID campaignId, UUID recipientId) {
        UUID tenant = TenantContext.get().orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required"));
        var campaign = campaigns.findByIdAndTenantId(campaignId, tenant)
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
        var recipient = recipientId == null
                ? recipients.findByCampaignIdAndTenantIdOrderByCreatedAtAsc(campaignId, tenant, PageRequest.of(0, 1)).stream().findFirst()
                : recipients.findByIdAndCampaignIdAndTenantId(recipientId, campaignId, tenant);
        var sample = recipient.orElseThrow(() -> new ApiException(409, "NO_PREVIEW_RECIPIENT", "Add an audience to preview recipient personalization"));
        Map<String, Object> values = sample.getVariables();
        if (campaign.getTemplateId() != null) {
            var version = templates.requireRenderableVersion(tenant, campaign.getTemplateId(), campaign.getTemplateVersion());
            Map<String, Object> selected = new LinkedHashMap<>();
            version.getVariablesSchema().keySet().forEach(key -> { if (values.get(key) != null) selected.put(key, values.get(key)); });
            var rendered = templates.render(version, selected);
            return new Preview(sample.getEmail(), campaign.getSender(), campaign.getReplyTo(), rendered.subject(), rendered.htmlContent(), rendered.textContent());
        }
        return new Preview(sample.getEmail(), campaign.getSender(), campaign.getReplyTo(),
                personalizer.render(campaign.getSubject(), values, false),
                personalizer.render(campaign.getHtmlBody(), values, true), personalizer.render(campaign.getTextBody(), values, false));
    }

    @Transactional
    public EmailMessageResponse test(UUID id, TestRequest request) {
        authorization.requireSender();
        Preview rendered = preview(id, request.recipientId());
        if (rendered.sender() == null) throw new ApiException(400, "CAMPAIGN_INCOMPLETE", "Set a verified sender before sending a test");
        return emails.send(new SendEmailRequest(rendered.sender(), List.of(request.to()), List.of(), List.of(),
                rendered.replyTo(), rendered.subject(), rendered.html(), rendered.text(), null, null, Map.of(),
                Map.of("campaignTestId", id.toString(), "attachmentIds", campaigns.findByIdAndTenantId(id,TenantContext.get().orElseThrow()).orElseThrow().getAttachmentIds().stream().map(UUID::toString).toList())), null);
    }

    public record Preview(String recipient, String sender, String replyTo, String subject, String html, String text) {}
    public record TestRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max=320) String to, UUID recipientId) {}
}
