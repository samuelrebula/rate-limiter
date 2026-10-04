package io.github.samuelrebula.ratelimit.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.time.Duration;
import java.util.Objects;

public final class RedisRateLimiter implements RateLimiter {

    private static final long TOKENS_PER_REQUEST = 1;

    private final ProxyManager<String> proxyManager;

    public RedisRateLimiter(ProxyManager<String> proxyManager) {
        this.proxyManager = Objects.requireNonNull(proxyManager, "proxyManager");
    }

    @Override
    public RateLimitDecision consume(RateLimitPolicy policy, String consumerKey) {
        Objects.requireNonNull(policy, "policy");
        String key = policy.storageKey(consumerKey);
        try {
            Bucket bucket = proxyManager.builder().build(key, () -> configuration(policy));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(TOKENS_PER_REQUEST);
            return new RateLimitDecision(
                    probe.isConsumed(),
                    probe.getRemainingTokens(),
                    policy.capacity(),
                    Duration.ofNanos(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException exception) {
            throw new RateLimitStoreException("Failed to consume a rate limit token from Redis", exception);
        }
    }

    private static BucketConfiguration configuration(RateLimitPolicy policy) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(policy.capacity())
                        .refillGreedy(policy.refillTokens(), policy.refillPeriod()))
                .build();
    }
}
