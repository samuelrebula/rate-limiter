package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitStoreHealthIndicatorTest {

    @Test
    void reportsUpWhenTheStoreResponds() {
        assertThat(RateLimitStoreHealthIndicator.check(() -> { }).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenTheStoreFailsWithoutDetails() {
        var health = RateLimitStoreHealthIndicator.check(() -> {
            throw new IllegalStateException("redis down");
        });
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }
}
