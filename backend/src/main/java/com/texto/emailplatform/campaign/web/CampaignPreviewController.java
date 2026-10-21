package com.texto.emailplatform.campaign.web;

import com.texto.emailplatform.campaign.CampaignPreviewService;
import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.email.api.EmailMessageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campaigns/{id}")
@PreAuthorize("@campaignAuthorizationService.canRead()")
public class CampaignPreviewController {
    private final CampaignPreviewService service;
    public CampaignPreviewController(CampaignPreviewService service) { this.service = service; }
    @GetMapping("/preview")
    public ApiResponse<CampaignPreviewService.Preview> preview(@PathVariable UUID id, @RequestParam(required=false) UUID recipientId) {
        return ApiResponse.ok(service.preview(id, recipientId));
    }
    @PostMapping("/test")
    @PreAuthorize("@campaignAuthorizationService.canManage()")
    public ApiResponse<EmailMessageResponse> test(@PathVariable UUID id, @Valid @RequestBody CampaignPreviewService.TestRequest request) {
        return ApiResponse.ok(service.test(id, request));
    }
}
