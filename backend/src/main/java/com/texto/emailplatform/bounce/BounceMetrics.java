package com.texto.emailplatform.bounce;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Bounce/DSN counters. Labels are classification and outcome only — no tokens or addresses.
 */
@Component
public class BounceMetrics {

    private final MeterRegistry meterRegistry;

    public BounceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementEvent() {
        Counter.builder("bounce_events_total").register(meterRegistry).increment();
    }

    public void incrementHard() {
        Counter.builder("hard_bounces_total").register(meterRegistry).increment();
    }

    public void incrementSoft() {
        Counter.builder("soft_bounces_total").register(meterRegistry).increment();
    }

    public void incrementUncorrelated() {
        Counter.builder("uncorrelated_bounces_total").register(meterRegistry).increment();
    }

    public void incrementSuppressedFromBounce() {
        Counter.builder("suppressed_from_bounce_total").register(meterRegistry).increment();
    }

    public void incrementDuplicate() {
        Counter.builder("duplicate_bounces_total").register(meterRegistry).increment();
    }

    public void recordClassification(BounceClass bounceClass) {
        if (bounceClass == BounceClass.HARD_BOUNCE) {
            incrementHard();
        } else if (bounceClass == BounceClass.SOFT_BOUNCE) {
            incrementSoft();
        }
    }
}
