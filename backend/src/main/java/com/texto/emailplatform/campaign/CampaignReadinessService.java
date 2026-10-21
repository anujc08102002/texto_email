package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.domain.DomainService;
import com.texto.emailplatform.template.TemplateService;
import org.springframework.stereotype.Service;

@Service
public class CampaignReadinessService {
    private final DomainService domains;
    private final TemplateService templates;
    public CampaignReadinessService(DomainService domains, TemplateService templates) {
        this.domains = domains;
        this.templates = templates;
    }

    public void validate(CampaignEntity campaign) {
        if (campaign.getSender() == null || campaign.getSender().isBlank()) {
            throw new ApiException(400, "CAMPAIGN_INCOMPLETE", "A verified sender is required before sending");
        }
        domains.requireVerifiedSender(campaign.getTenantId(), campaign.getSender());
        if (campaign.getTemplateId() != null) {
            var version = templates.requireRenderableVersion(campaign.getTenantId(), campaign.getTemplateId(), campaign.getTemplateVersion());
            campaign.pinTemplateVersion(version.getVersion());
        } else if (campaign.getSubject() == null || campaign.getSubject().isBlank()
                || ((campaign.getHtmlBody() == null || campaign.getHtmlBody().isBlank())
                && (campaign.getTextBody() == null || campaign.getTextBody().isBlank()))) {
            throw new ApiException(400, "CAMPAIGN_INCOMPLETE", "Subject and email content are required before sending");
        }
    }

    public static void validateTimezone(String timezone) {
        try { java.time.ZoneId.of(timezone); }
        catch (RuntimeException exception) { throw new ApiException(400, "INVALID_TIMEZONE", "Choose a valid IANA timezone"); }
    }
}
