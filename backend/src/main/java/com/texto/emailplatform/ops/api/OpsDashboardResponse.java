package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OpsDashboardResponse(
        UUID tenantId,
        long totalMessages,
        long queued,
        long processing,
        long sending,
        long delivered,
        long deferred,
        long failed,
        long bounced,
        long suppressed,
        long complaints,
        double deliveryRate,
        double bounceRate,
        double complaintRate,
        List<OpsActivityItem> recentActivity
) {

    public record OpsActivityItem(
            Instant at,
            String type,
            UUID tenantId,
            UUID messageId,
            String status,
            String summary
    ) {
    }
}
