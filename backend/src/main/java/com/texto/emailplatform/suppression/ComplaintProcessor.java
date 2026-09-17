package com.texto.emailplatform.suppression;

import java.util.UUID;

/**
 * Internal suppression helper. Tenant identity must already be resolved from a correlated
 * outbound message. Do not call with a tenant id taken from an inbound complaint payload.
 * Feedback-loop ingestion uses ComplaintIngestionService, not this interface.
 */
public interface ComplaintProcessor {

    void processComplaint(UUID tenantId, String email, UUID messageId, String reason);
}
