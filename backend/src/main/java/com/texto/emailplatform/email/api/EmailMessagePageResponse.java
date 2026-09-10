package com.texto.emailplatform.email.api;

import java.util.List;

public record EmailMessagePageResponse(
        List<EmailMessageResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
