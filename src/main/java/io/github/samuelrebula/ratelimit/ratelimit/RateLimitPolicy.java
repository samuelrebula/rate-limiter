package io.github.samuelrebula.ratelimit.ratelimit;

import java.time.Duration;
import java.util.List;

public record RateLimitPolicy(
        String name,
        List<String> patterns,
        long capacity,
        long refillTokens,
        Duration refillPeriod
) {

    public RateLimitPolicy {
        if (name == null || name.isBlank() || name.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "rate-limit policy name must be non-blank and must not contain ':'");
        }
        if (patterns == null || patterns.isEmpty()
                || patterns.stream().anyMatch(pattern -> pattern == null || pattern.isBlank())) {
            throw new IllegalArgumentException(
                    "rate-limit policy '" + name + "' must declare at least one path pattern");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException(
                    "rate-limit policy '" + name + "' capacity must be >= 1");
        }
        if (refillTokens < 1) {
            throw new IllegalArgumentException(
                    "rate-limit policy '" + name + "' refill-tokens must be >= 1");
        }
        if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException(
                    "rate-limit policy '" + name + "' refill-period must be positive");
        }
        patterns = List.copyOf(patterns);
    }

    public String storageKey(String consumerKey) {
        if (consumerKey == null || consumerKey.isBlank()) {
            throw new IllegalArgumentException("consumerKey is required");
        }
        return "rl:" + name + ":" + consumerKey;
    }
}
