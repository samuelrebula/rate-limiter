package io.github.samuelrebula.ratelimit.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalRateLimiter implements RateLimiter {

    private static final long TOKENS_PER_REQUEST = 1;

    private final TimeMeter timeMeter;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LocalRateLimiter() {
        this(TimeMeter.SYSTEM_NANOTIME);
    }

    LocalRateLimiter(TimeMeter timeMeter) {
        this.timeMeter = Objects.requireNonNull(timeMeter, "timeMeter");
    }

    @Override
    public RateLimitDecision consume(RateLimitPolicy policy, String consumerKey) {
        Objects.requireNonNull(policy, "policy");
        Bucket bucket = buckets.computeIfAbsent(policy.storageKey(consumerKey), ignored -> newBucket(policy));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(TOKENS_PER_REQUEST);
        return new RateLimitDecision(
                probe.isConsumed(),
                probe.getRemainingTokens(),
                policy.capacity(),
                Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket(RateLimitPolicy policy) {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(policy.capacity())
                        .refillGreedy(policy.refillTokens(), policy.refillPeriod()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}
