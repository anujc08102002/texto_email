package com.texto.emailplatform.suppression;

import org.springframework.stereotype.Service;

@Service
public class DefaultComplaintProcessor implements ComplaintProcessor {

    private final SuppressionService suppressionService;

    public DefaultComplaintProcessor(SuppressionService suppressionService) {
        this.suppressionService = suppressionService;
    }

    @Override
    public void processComplaint(java.util.UUID tenantId, String email, java.util.UUID messageId, String reason) {
        suppressionService.recordComplaint(tenantId, email, messageId, reason);
    }
}
