package com.texto.emailplatform.segment.api;

import com.texto.emailplatform.contact.api.ContactResponse;
import java.util.List;

public record SegmentPreviewResponse(
        List<ContactResponse> items,
        long totalElements,
        int page,
        int size,
        int totalPages
) {}