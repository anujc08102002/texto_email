package com.texto.emailplatform.campaign.web;

import com.texto.emailplatform.campaign.CampaignLifecycleService;
import com.texto.emailplatform.campaign.api.CampaignResponse;
import com.texto.emailplatform.campaign.api.ScheduleCampaignRequest;
import com.texto.emailplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canRead()")
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignLifecycleController {
    private final CampaignLifecycleService service;

    public CampaignLifecycleController(CampaignLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/{id}/send")
    public ApiResponse<CampaignResponse> sendNow(@PathVariable UUID id) {
        return ApiResponse.ok(service.sendNow(id));
    }

    @PostMapping("/{id}/reschedule")
    public ApiResponse<CampaignResponse> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleCampaignRequest request
    ) {
        return ApiResponse.ok(service.reschedule(id, request));
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<CampaignResponse> resume(@PathVariable UUID id) {
        return ApiResponse.ok(service.resume(id));
    }
}