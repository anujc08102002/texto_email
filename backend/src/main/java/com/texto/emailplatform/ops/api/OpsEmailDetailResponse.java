package com.texto.emailplatform.ops.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OpsEmailDetailResponse(
        OpsEmailSummaryResponse message,
        Map<String, Instant> timestamps,
        List<OpsTimelineEvent> timeline,
        List<OpsDeliveryAttemptResponse> deliveryAttempts,
        List<OpsBounceEventResponse> bounces,
        List<OpsComplaintEventResponse> complaints,
        List<OpsSuppressionResponse> suppressions
) {
}
