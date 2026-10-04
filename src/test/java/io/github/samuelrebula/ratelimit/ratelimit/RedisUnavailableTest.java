package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisUnavailableTest {

    @Test
    void unreachableRedisFailsBeforeTheApplicationCanServe() {
        RateLimitPolicy policy = new RateLimitPolicy("down", List.of("/api/**"), 1, 1, Duration.ofMinutes(1));

        assertThatThrownBy(() -> {
            try (RedisRateLimitConnection connection = RedisRateLimitConnection.open(
                    "127.0.0.1",
                    1,
                    0,
                    null,
                    null,
                    Duration.ofMillis(200),
                    Duration.ofSeconds(10)
            )) {
                connection.rateLimiter().consume(policy, "203.0.113.90");
            }
        }).isInstanceOf(RateLimitStoreException.class);
    }
}
