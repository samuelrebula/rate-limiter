package io.github.samuelrebula.ratelimit.ratelimit;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Pings the Redis connection used by the limiter. Present only when storage is Redis.
 */
final class RateLimitStoreHealthIndicator implements HealthIndicator {

    private final RedisRateLimitConnection connection;

    RateLimitStoreHealthIndicator(RedisRateLimitConnection connection) {
        this.connection = connection;
    }

    @Override
    public Health health() {
        return check(connection::ping);
    }

    static Health check(Runnable ping) {
        try {
            ping.run();
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down().build();
        }
    }
}
