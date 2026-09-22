package com.texto.emailplatform.ops.api;

import java.time.Instant;

public record OpsTimelineEvent(Instant at, String type, String status, String detail) {
}
