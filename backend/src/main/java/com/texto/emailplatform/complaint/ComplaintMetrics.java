package com.texto.emailplatform.complaint;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Complaint counters. Labels are outcome only — no tokens, addresses, or tenant ids.
 */
@Component
public class ComplaintMetrics {

    private final MeterRegistry meterRegistry;

    public ComplaintMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementEvent() {
        Counter.builder("complaint_events_total").register(meterRegistry).increment();
    }

    public void incrementCorrelated() {
        Counter.builder("complaints_correlated_total").register(meterRegistry).increment();
    }

    public void incrementUncorrelated() {
        Counter.builder("complaints_uncorrelated_total").register(meterRegistry).increment();
    }

    public void incrementSuppressed() {
        Counter.builder("complaint_suppressions_total").register(meterRegistry).increment();
    }

    public void incrementDuplicate() {
        Counter.builder("duplicate_complaints_total").register(meterRegistry).increment();
    }
}
