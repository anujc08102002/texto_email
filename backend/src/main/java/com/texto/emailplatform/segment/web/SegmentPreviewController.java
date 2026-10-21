package com.texto.emailplatform.segment.web;

import com.texto.emailplatform.common.api.ApiResponse;
import com.texto.emailplatform.segment.SegmentPreviewService;
import com.texto.emailplatform.segment.api.SegmentPreviewRequest;
import com.texto.emailplatform.segment.api.SegmentPreviewResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.security.access.prepost.PreAuthorize("@campaignAuthorizationService.canRead()")
@RestController
@RequestMapping("/api/v1/segments")
public class SegmentPreviewController {
    private final SegmentPreviewService service;

    public SegmentPreviewController(SegmentPreviewService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public ApiResponse<SegmentPreviewResponse> preview(@Valid @RequestBody SegmentPreviewRequest request) {
        return ApiResponse.ok(service.previewDefinition(request.definition(), request.page(), request.size()));
    }

    @PostMapping("/{id}/preview")
    public ApiResponse<SegmentPreviewResponse> previewSaved(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(service.previewSaved(id, page, size));
    }
}