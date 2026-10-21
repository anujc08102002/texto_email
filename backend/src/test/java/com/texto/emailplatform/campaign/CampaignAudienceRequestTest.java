package com.texto.emailplatform.campaign;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampaignAudienceRequestTest {
    @Test void segmentRequestMayOmitAllSubscribedFlag() {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        UUID segment = UUID.randomUUID();
        var request = json.readValue("{\"segmentId\":\"" + segment + "\"}", CampaignAudienceService.AudienceRequest.class);
        assertThat(request.segmentId()).isEqualTo(segment);
        assertThat(Boolean.TRUE.equals(request.allSubscribed())).isFalse();
    }
}
