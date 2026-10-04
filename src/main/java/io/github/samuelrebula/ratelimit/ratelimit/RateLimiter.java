package io.github.samuelrebula.ratelimit.ratelimit;

public interface RateLimiter {

    RateLimitDecision consume(RateLimitPolicy policy, String consumerKey);
}
