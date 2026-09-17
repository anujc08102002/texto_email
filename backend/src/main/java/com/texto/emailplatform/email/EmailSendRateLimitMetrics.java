package com.texto.emailplatform.email;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Send-rate counters. {@code result} is the only tag — no tenant IDs.
 */
@Component
public class EmailSendRateLimitMetrics {

    static final String METRIC_NAME = "email_rate_limit";

    private final MeterRegistry meterRegistry;

    public EmailSendRateLimitMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAllowed() {
        increment("allowed");
    }

    public void recordRejected() {
        increment("rejected");
    }

    public void recordError() {
        increment("error");
    }

    private void increment(String result) {
        Counter.builder(METRIC_NAME)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
