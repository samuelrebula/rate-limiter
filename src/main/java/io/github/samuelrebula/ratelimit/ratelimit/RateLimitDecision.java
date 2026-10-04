package io.github.samuelrebula.ratelimit.ratelimit;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        long remainingTokens,
        long limit,
        Duration retryAfter
) {

    public RateLimitDecision {
        if (remainingTokens < 0) {
            throw new IllegalArgumentException("remainingTokens must be >= 0");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be zero or positive");
        }
    }
}
