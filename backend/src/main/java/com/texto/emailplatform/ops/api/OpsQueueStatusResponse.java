package com.texto.emailplatform.ops.api;

import java.util.List;

public record OpsQueueStatusResponse(List<OpsQueueDepth> queues) {

    public record OpsQueueDepth(String name, long messages, int consumers, boolean available) {
    }
}
