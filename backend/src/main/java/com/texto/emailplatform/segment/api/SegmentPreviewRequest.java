package com.texto.emailplatform.segment.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SegmentPreviewRequest(
        @NotBlank @jakarta.validation.constraints.Size(max=50_000) String definition,
        @Min(0) int page,
        @Min(1) @Max(100) int size
) {}