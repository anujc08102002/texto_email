package com.texto.emailplatform.campaign.api;
import java.util.List; public record CampaignPageResponse(List<CampaignResponse> items,int page,int size,long totalElements,int totalPages){}
