package io.github.samuelrebula.ratelimit.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Records rate limit decisions. Tags are the policy name and, for a decision, the result.
 * The client address is never a tag.
 */
final class RateLimitMetrics {

    static final String REQUESTS = "rate.limit.requests";
    static final String STORE_ERRORS = "rate.limit.store.errors";
    static final String CONSUME = "rate.limit.consume";

    private final MeterRegistry registry;

    RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    Timer.Sample start() {
        return Timer.start(registry);
    }

    void recordDecision(Timer.Sample sample, String policyName, boolean allowed) {
        sample.stop(consumeTimer(policyName));
        Counter.builder(REQUESTS)
                .description("Rate limit decisions")
                .tag("policy", policyName)
                .tag("result", allowed ? "allowed" : "rejected")
                .register(registry)
                .increment();
    }

    void recordStoreError(Timer.Sample sample, String policyName) {
        sample.stop(consumeTimer(policyName));
        Counter.builder(STORE_ERRORS)
                .description("Rate limit store failures")
                .register(registry)
                .increment();
    }

    private Timer consumeTimer(String policyName) {
        return Timer.builder(CONSUME)
                .description("Time to consume one rate limit token")
                .tag("policy", policyName)
                .register(registry);
    }
}
