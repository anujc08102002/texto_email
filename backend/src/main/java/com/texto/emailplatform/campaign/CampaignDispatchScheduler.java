package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.CampaignRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(CampaignDispatchScheduler.class);
    private static final int CAMPAIGN_BATCH_SIZE = 10;

    private final CampaignRepository campaigns;
    private final CampaignDispatchService dispatchService;

    public CampaignDispatchScheduler(CampaignRepository campaigns, CampaignDispatchService dispatchService) {
        this.campaigns = campaigns;
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${email-platform.campaign.dispatch-poll-ms:1000}")
    public void dispatchDueCampaigns() {
        try {
            campaigns.findDispatchableIds(Instant.now(), PageRequest.of(0, CAMPAIGN_BATCH_SIZE))
                    .forEach(dispatchService::dispatch);
        } catch (RuntimeException exception) {
            log.warn("Campaign dispatch poll failed errorType={}", exception.getClass().getSimpleName());
        }
    }
}