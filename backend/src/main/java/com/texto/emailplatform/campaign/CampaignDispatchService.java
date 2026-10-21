package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.CampaignEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientEntity;
import com.texto.emailplatform.campaign.domain.CampaignRecipientRepository;
import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.campaign.domain.CampaignScheduleRepository;
import com.texto.emailplatform.common.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class CampaignDispatchService {
    private static final Logger log = LoggerFactory.getLogger(CampaignDispatchService.class);
    private static final int RECIPIENT_BATCH_SIZE = 50;

    private final CampaignRepository campaigns;
    private final CampaignRecipientRepository recipients;
    private final CampaignScheduleRepository schedules;
    private final CampaignRecipientSender sender;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public CampaignDispatchService(
            CampaignRepository campaigns,
            CampaignRecipientRepository recipients,
            CampaignScheduleRepository schedules,
            CampaignRecipientSender sender,
            org.springframework.transaction.PlatformTransactionManager transactionManager
    ) {
        this.campaigns = campaigns;
        this.recipients = recipients;
        this.schedules = schedules;
        this.sender = sender;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    public void dispatch(UUID campaignId) {
        Instant now = Instant.now();
        UUID leaseId = UUID.randomUUID();
        // Commit the claim before opening recipient transactions. Holding the campaign lock
        // across REQUIRES_NEW calls deadlocks and prevents stop requests from taking effect.
        CampaignEntity campaign = transactions.execute(status -> {
            if (campaigns.claimForDispatch(campaignId, now, leaseId, now.plusSeconds(300)) == 0) return null;
            schedules.invalidateActive(campaignId);
            return campaigns.findById(campaignId).orElse(null);
        });
        if (campaign == null) return;

        List<CampaignRecipientEntity> batch = recipients
                .findByCampaignIdAndTenantIdAndStatusOrderByCreatedAtAsc(
                        campaignId,
                        campaign.getTenantId(),
                        "PENDING",
                        PageRequest.of(0, RECIPIENT_BATCH_SIZE)
                );

        for (CampaignRecipientEntity recipient : batch) {
            try {
                sender.send(campaignId, campaign.getTenantId(), recipient.getId());
            } catch (RuntimeException exception) {
                if (isRetryable(exception)) {
                    log.info("Campaign dispatch deferred campaignId={} recipientId={} errorCode={}",
                            campaignId,
                            recipient.getId(),
                            exception instanceof ApiException apiException ? apiException.getCode() : "TEMPORARY_FAILURE");
                    break;
                }
                String message = exception.getMessage() == null
                        ? "Recipient could not be queued"
                        : exception.getMessage();
                sender.markFailed(campaignId, campaign.getTenantId(), recipient.getId(), message);
                log.warn("Campaign recipient dispatch failed campaignId={} recipientId={} errorType={}",
                        campaignId, recipient.getId(), exception.getClass().getSimpleName());
            }
        }

        transactions.executeWithoutResult(status -> {
            var current = campaigns.lockByIdAndTenantId(campaignId, campaign.getTenantId()).orElse(null);
            if (current == null || !"PROCESSING".equals(current.getStatus())
                    || !leaseId.equals(current.getDispatchLeaseId())) return;
            long pending = recipients.countByCampaignIdAndTenantIdAndStatus(campaignId, campaign.getTenantId(), "PENDING");
            if (pending > 0) { current.markQueued(); return; }
            long failed = recipients.countByCampaignIdAndTenantIdAndStatus(campaignId, campaign.getTenantId(), "FAILED");
            long accepted = recipients.countByCampaignIdAndTenantIdAndStatus(campaignId, campaign.getTenantId(), "ACCEPTED");
            long suppressed = recipients.countByCampaignIdAndTenantIdAndStatus(campaignId, campaign.getTenantId(), "SUPPRESSED");
            if (failed > 0 && accepted + suppressed == 0) current.fail();
            else current.complete(failed > 0);
        });
    }

    private static boolean isRetryable(RuntimeException exception) {
        return exception instanceof org.springframework.dao.TransientDataAccessException
                || exception instanceof org.springframework.transaction.TransactionException
                || exception instanceof ApiException apiException
                && (apiException.getStatus() == 429 || apiException.getStatus() == 503);
    }
}