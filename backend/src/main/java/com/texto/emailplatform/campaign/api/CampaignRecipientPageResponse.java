package com.texto.emailplatform.campaign.api;

import java.util.List;

public record CampaignRecipientPageResponse(
        List<CampaignRecipientResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}