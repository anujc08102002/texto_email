package com.texto.emailplatform.campaign;

/** MTA acceptance is deliberately distinguished from confirmed recipient delivery. */
public final class CampaignRecipientStates {
    private CampaignRecipientStates() {}
    public static final String EXPRESSION = """
            CASE
              WHEN EXISTS (SELECT 1 FROM complaint_events ce WHERE ce.email_message_id = m.id
                  AND ce.tenant_id = r.tenant_id AND ce.processing_status = 'APPLIED') THEN 'COMPLAINED'
              WHEN m.status = 'DELIVERED' THEN 'ACCEPTED'
              WHEN m.status IS NOT NULL THEN m.status
              ELSE r.status
            END
            """;
}
