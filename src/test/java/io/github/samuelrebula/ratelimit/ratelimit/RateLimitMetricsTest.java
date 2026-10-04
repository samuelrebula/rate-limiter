package io.github.samuelrebula.ratelimit.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RateLimitMetrics metrics = new RateLimitMetrics(registry);

    @Test
    void countsAllowedAndRejectedByPolicyAndResult() {
        metrics.recordDecision(metrics.start(), "expensive", true);
        metrics.recordDecision(metrics.start(), "default", false);

        Counter allowed = registry.get(RateLimitMetrics.REQUESTS)
                .tag("policy", "expensive")
                .tag("result", "allowed")
                .counter();
        Counter rejected = registry.get(RateLimitMetrics.REQUESTS)
                .tag("policy", "default")
                .tag("result", "rejected")
                .counter();
        assertThat(allowed.count()).isEqualTo(1);
        assertThat(rejected.count()).isEqualTo(1);
        assertThat(allowed.getId().getTags()).extracting("key").containsExactlyInAnyOrder("policy", "result");

        Timer expensive = registry.get(RateLimitMetrics.CONSUME).tag("policy", "expensive").timer();
        Timer fallback = registry.get(RateLimitMetrics.CONSUME).tag("policy", "default").timer();
        assertThat(expensive.count()).isEqualTo(1);
        assertThat(fallback.count()).isEqualTo(1);
        assertThat(expensive.getId().getTags()).extracting("key").containsExactly("policy");
    }

    @Test
    void countsAStoreErrorWithoutADecision() {
        metrics.recordStoreError(metrics.start(), "default");

        Counter errors = registry.get(RateLimitMetrics.STORE_ERRORS).counter();
        assertThat(errors.count()).isEqualTo(1);
        assertThat(errors.getId().getTags()).isEmpty();
        assertThat(registry.get(RateLimitMetrics.CONSUME).tag("policy", "default").timer().count()).isEqualTo(1);
        assertThat(registry.find(RateLimitMetrics.REQUESTS).counters()).isEmpty();
    }
}
