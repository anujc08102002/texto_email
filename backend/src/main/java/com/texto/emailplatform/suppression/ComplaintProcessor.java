package com.texto.emailplatform.suppression;

import java.util.UUID;

public interface ComplaintProcessor {

    void processComplaint(UUID tenantId, String email, UUID messageId, String reason);
}
