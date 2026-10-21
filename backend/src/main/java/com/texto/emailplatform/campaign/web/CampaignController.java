package com.texto.emailplatform.campaign.web;

import com.texto.emailplatform.campaign.CampaignService;
import com.texto.emailplatform.campaign.api.CampaignPageResponse;
import com.texto.emailplatform.campaign.api.CampaignRecipientPageResponse;
import com.texto.emailplatform.campaign.api.CampaignRecipientRequest;
import com.texto.emailplatform.campaign.api.CampaignRecipientResponse;
import com.texto.emailplatform.campaign.api.CampaignReportResponse;
import com.texto.emailplatform.campaign.api.CampaignResponse;
import com.texto.emailplatform.campaign.api.SaveCampaignDraftRequest;
import com.texto.emailplatform.campaign.api.ScheduleCampaignRequest;
import com.texto.emailplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canRead()")
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {
	private final CampaignService service;
	private final com.texto.emailplatform.campaign.CampaignAudienceService audiences;
	private final com.texto.emailplatform.campaign.CampaignReportExportService exports;

	public CampaignController(CampaignService service, com.texto.emailplatform.campaign.CampaignAudienceService audiences,
            com.texto.emailplatform.campaign.CampaignReportExportService exports) {
		this.service = service;
		this.audiences = audiences;
		this.exports = exports;
	}

    @GetMapping(value="/{id}/export", produces="text/csv")
    public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> export(@PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=campaign-" + id + ".csv")
                .header("Cache-Control", "no-store")
                .body(exports.export(id));
    }

	@PostMapping("/{id}/audience")
	public ApiResponse<com.texto.emailplatform.campaign.CampaignAudienceService.AudienceResult> audience(
			@PathVariable UUID id,
			@RequestBody com.texto.emailplatform.campaign.CampaignAudienceService.AudienceRequest request) {
		return ApiResponse.ok(audiences.add(id, request));
	}

	@GetMapping
	public ApiResponse<CampaignPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
	) {
		return ApiResponse.ok(service.list(page, size, q, status, sort, direction));
	}

	@GetMapping("/{id}")
	public ApiResponse<CampaignResponse> get(@PathVariable UUID id) {
		return ApiResponse.ok(service.get(id));
	}

	@PostMapping("/drafts")
	public ApiResponse<CampaignResponse> saveDraft(@Valid @RequestBody SaveCampaignDraftRequest request) {
		return ApiResponse.ok(service.saveDraft(request));
	}

    @GetMapping("/{id}/content")
    public ApiResponse<SaveCampaignDraftRequest> content(@PathVariable UUID id) {
        return ApiResponse.ok(service.content(id));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    public ApiResponse<CampaignResponse> update(@PathVariable UUID id, @Valid @RequestBody SaveCampaignDraftRequest request) {
        return ApiResponse.ok(service.updateDraft(id, request));
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<CampaignResponse> cloneDraft(@PathVariable UUID id) {
        return ApiResponse.ok(service.cloneDraft(id));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/recipients/{recipientId}")
    public ApiResponse<Void> removeRecipient(@PathVariable UUID id, @PathVariable UUID recipientId) {
        service.removeRecipient(id, recipientId);
        return ApiResponse.ok(null);
    }

	@PostMapping("/{id}/recipients")
	public ApiResponse<List<CampaignRecipientResponse>> addRecipients(
			@PathVariable UUID id,
			@Valid @RequestBody CampaignRecipientRequest request
	) {
		return ApiResponse.ok(service.addRecipients(id, request));
	}

	@GetMapping("/{id}/recipients")
	public ApiResponse<CampaignRecipientPageResponse> recipients(
			@PathVariable UUID id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size
	) {
		return ApiResponse.ok(service.listRecipients(id, page, size));
	}

	@GetMapping("/{id}/report")
	public ApiResponse<CampaignReportResponse> report(@PathVariable UUID id) {
		return ApiResponse.ok(service.report(id));
	}

	@PostMapping("/{id}/schedule")
	public ApiResponse<CampaignResponse> schedule(
			@PathVariable UUID id,
			@Valid @RequestBody ScheduleCampaignRequest request
	) {
		return ApiResponse.ok(service.schedule(id, request));
	}

	@PostMapping("/{id}/stop")
	public ApiResponse<CampaignResponse> stop(@PathVariable UUID id) {
		return ApiResponse.ok(service.stop(id));
	}
}
