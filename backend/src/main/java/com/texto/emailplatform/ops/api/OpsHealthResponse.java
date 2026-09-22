package com.texto.emailplatform.ops.api;

import java.util.Map;

public record OpsHealthResponse(
        String status,
        Map<String, String> components
) {
}
