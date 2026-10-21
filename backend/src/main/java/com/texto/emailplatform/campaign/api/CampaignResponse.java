package com.texto.emailplatform.campaign.api;
import java.time.Instant; import java.util.UUID;
public record CampaignResponse(UUID id,String name,String status,String sender,String replyTo,String subject,String preheader,UUID templateId,Instant scheduledAt,String timezone,Instant createdAt,Instant updatedAt){}
