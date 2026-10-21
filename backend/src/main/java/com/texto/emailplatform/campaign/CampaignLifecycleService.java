package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.api.CampaignResponse;
import com.texto.emailplatform.campaign.api.ScheduleCampaignRequest;
import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientRepository;
import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.campaign.domain.CampaignScheduleEntity;
import com.texto.emailplatform.campaign.domain.CampaignScheduleRepository;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignLifecycleService {
    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final CampaignScheduleRepository schedules;
    private final CampaignAuthorizationService authorization;
    private final CampaignReadinessService readiness;

    public CampaignLifecycleService(
            CampaignRepository campaigns,
            CampaignRecipientRepository recipients,
            CampaignScheduleRepository schedules,
            CampaignAuthorizationService authorization,
            CampaignReadinessService readiness
    ) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.schedules = schedules;
        this.authorization = authorization;
        this.readiness = readiness;
    }

    @Transactional
    public CampaignResponse sendNow(UUID id) {
        authorization.requireSender();
        CampaignEntity campaign = requireOwned(id);
        requireRecipients(campaign);
        readiness.validate(campaign);
        try {
            campaign.queue();
        } catch (IllegalStateException exception) {
            throw new ApiException(409, "CAMPAIGN_NOT_SENDABLE", exception.getMessage());
        }
        schedules.invalidateActive(campaign.getId());
        return response(campaign);
    }

    @Transactional
    public CampaignResponse reschedule(UUID id, ScheduleCampaignRequest request) {
        authorization.requireSender();
        CampaignEntity campaign = requireOwned(id);
        if (!"SCHEDULED".equals(campaign.getStatus())) {
            throw new ApiException(409, "CAMPAIGN_NOT_SCHEDULED", "Only scheduled campaigns can be rescheduled");
        }
        requireRecipients(campaign);
        readiness.validate(campaign);
        CampaignReadinessService.validateTimezone(request.timezone());
        schedules.invalidateActive(campaign.getId());
        campaign.schedule(request.scheduledAt(), request.timezone().trim());
        schedules.save(CampaignScheduleEntity.active(
                campaign.getId(),
                campaign.getTenantId(),
                request.scheduledAt(),
                request.timezone().trim()
        ));
        return response(campaign);
    }

    @Transactional
    public CampaignResponse resume(UUID id) {
        authorization.requireSender();
        CampaignEntity campaign = requireOwned(id);
        if (!"STOPPED".equals(campaign.getStatus())) {
            throw new ApiException(409, "CAMPAIGN_NOT_STOPPED", "Only stopped campaigns can be resumed");
        }
        requireRecipients(campaign);
        readiness.validate(campaign);
        campaign.resume();
        return response(campaign);
    }

    private void requireRecipients(CampaignEntity campaign) {
        if (recipients.countByCampaignIdAndTenantId(campaign.getId(), campaign.getTenantId()) == 0) {
            throw new ApiException(409, "CAMPAIGN_HAS_NO_RECIPIENTS", "Add at least one eligible recipient before sending");
        }
    }

    private CampaignEntity requireOwned(UUID id) {
        UUID tenantId = tenant();
        return campaigns.lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
    }

    private static CampaignResponse response(CampaignEntity campaign) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getSender(),
                campaign.getReplyTo(),
                campaign.getSubject(),
                campaign.getPreheader(),
                campaign.getTemplateId(),
                campaign.getScheduledAt(),
                campaign.getTimezone(),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }

    private static UUID tenant() {
        return TenantContext.get().orElseThrow(
                () -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required")
        );
    }
}