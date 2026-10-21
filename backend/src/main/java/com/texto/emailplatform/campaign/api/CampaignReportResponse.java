package com.texto.emailplatform.campaign.api;
import java.util.UUID;
/** Counts are recipient states. Provider acceptance is not recipient delivery. */
public record CampaignReportResponse(UUID campaignId,long total,long pending,long queued,long accepted,long delivered,long bounced,long complained,long failed,long suppressed){}
