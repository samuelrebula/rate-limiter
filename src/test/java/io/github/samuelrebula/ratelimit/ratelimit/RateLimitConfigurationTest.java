package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitConfigurationTest {

    @Test
    void localStorageDoesNotOpenRedis() {
        RateLimiter limiter = RateLimitConfiguration.create(
                new RateLimitProperties("local", false, null, List.of()),
                () -> {
                    throw new AssertionError("Redis connection should not be opened");
                });

        assertThat(limiter).isInstanceOf(LocalRateLimiter.class);
    }

    @Test
    void rejectsUnknownStorage() {
        RateLimitProperties properties = new RateLimitProperties("memory", false, null, List.of());

        assertThatThrownBy(() -> RateLimitConfiguration.create(properties, () -> {
            throw new AssertionError("Redis connection should not be opened");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("memory");
    }
}
