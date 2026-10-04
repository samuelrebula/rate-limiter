package io.github.samuelrebula.ratelimit.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRateLimiterTest {

    private static final String CONSUMER = "203.0.113.10";

    private final ManualTimeMeter clock = new ManualTimeMeter();
    private final LocalRateLimiter limiter = new LocalRateLimiter(clock);

    @Test
    void allowsRequestsWhileTokensRemain() {
        RateLimitPolicy policy = policy("default", 3, 3, Duration.ofMinutes(1));

        RateLimitDecision first = limiter.consume(policy, CONSUMER);
        RateLimitDecision second = limiter.consume(policy, CONSUMER);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingTokens()).isEqualTo(2);
        assertThat(first.limit()).isEqualTo(3);
        assertThat(first.retryAfter()).isZero();
        assertThat(second.allowed()).isTrue();
        assertThat(second.remainingTokens()).isEqualTo(1);
    }

    @Test
    void rejectsRequestWhenCapacityIsExhausted() {
        RateLimitPolicy policy = policy("default", 2, 2, Duration.ofMinutes(1));

        limiter.consume(policy, CONSUMER);
        RateLimitDecision lastAllowed = limiter.consume(policy, CONSUMER);
        RateLimitDecision rejected = limiter.consume(policy, CONSUMER);

        assertThat(lastAllowed.allowed()).isTrue();
        assertThat(lastAllowed.remainingTokens()).isZero();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remainingTokens()).isZero();
        assertThat(rejected.limit()).isEqualTo(2);
        assertThat(rejected.retryAfter()).isPositive();
    }

    @Test
    void burstAllowsCapacityImmediatelyThenRejects() {
        RateLimitPolicy policy = policy("burst", 5, 1, Duration.ofSeconds(10));

        for (int remaining = 4; remaining >= 0; remaining--) {
            RateLimitDecision decision = limiter.consume(policy, CONSUMER);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remainingTokens()).isEqualTo(remaining);
        }

        RateLimitDecision rejected = limiter.consume(policy, CONSUMER);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void refillsOneTokenPerPeriodWithoutRestoringFullCapacity() {
        RateLimitPolicy policy = policy("burst", 5, 1, Duration.ofSeconds(10));
        for (int i = 0; i < 5; i++) {
            limiter.consume(policy, CONSUMER);
        }

        clock.advance(Duration.ofSeconds(9));
        RateLimitDecision tooEarly = limiter.consume(policy, CONSUMER);
        assertThat(tooEarly.allowed()).isFalse();
        assertThat(tooEarly.retryAfter()).isEqualTo(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        RateLimitDecision refilled = limiter.consume(policy, CONSUMER);
        assertThat(refilled.allowed()).isTrue();
        assertThat(refilled.remainingTokens()).isZero();

        RateLimitDecision stillEmpty = limiter.consume(policy, CONSUMER);
        assertThat(stillEmpty.allowed()).isFalse();
        assertThat(stillEmpty.retryAfter()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void keepsBucketsIndependentPerConsumerAndPolicy() {
        RateLimitPolicy alpha = policy("alpha", 1, 1, Duration.ofMinutes(1));
        RateLimitPolicy beta = policy("beta", 1, 1, Duration.ofMinutes(1));

        assertThat(limiter.consume(alpha, "203.0.113.1").allowed()).isTrue();
        assertThat(limiter.consume(alpha, "203.0.113.1").allowed()).isFalse();
        assertThat(limiter.consume(alpha, "203.0.113.2").allowed()).isTrue();
        assertThat(limiter.consume(beta, "203.0.113.1").allowed()).isTrue();
    }

    private static RateLimitPolicy policy(String name, long capacity, long refillTokens, Duration refillPeriod) {
        return new RateLimitPolicy(name, List.of("/api/**"), capacity, refillTokens, refillPeriod);
    }

    private static final class ManualTimeMeter implements TimeMeter {

        private long nanos = TimeUnit.MINUTES.toNanos(1);

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
