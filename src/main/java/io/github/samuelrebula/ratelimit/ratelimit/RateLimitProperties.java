package io.github.samuelrebula.ratelimit.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        String storage,
        boolean trustForwardedHeaders,
        Redis redis,
        List<Policy> policies
) {

    public RateLimitProperties {
        storage = (storage == null || storage.isBlank()) ? "local" : storage;
        redis = redis == null ? new Redis(null, null) : redis;
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public record Redis(Duration commandTimeout, Duration keyExpirationMargin) {

        public Redis {
            if (commandTimeout == null) {
                commandTimeout = Duration.ofSeconds(1);
            }
            if (keyExpirationMargin == null) {
                keyExpirationMargin = Duration.ofSeconds(10);
            }
            if (commandTimeout.isZero() || commandTimeout.isNegative()) {
                throw new IllegalArgumentException("rate-limit.redis.command-timeout must be positive");
            }
            if (keyExpirationMargin.isNegative()) {
                throw new IllegalArgumentException("rate-limit.redis.key-expiration-margin must be zero or positive");
            }
        }
    }

    public record Policy(
            String name,
            List<String> patterns,
            long capacity,
            long refillTokens,
            Duration refillPeriod
    ) {
    }
}
