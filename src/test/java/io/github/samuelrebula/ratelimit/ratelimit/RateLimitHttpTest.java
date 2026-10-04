package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitHttpTest {

    private final RateLimitPolicy policy = new RateLimitPolicy(
            "default", List.of("/api/**"), 100, 100, Duration.ofMinutes(1));

    @Test
    void advertisesCapacityAndRefillPeriod() {
        assertEquals("\"default\";q=100;w=60", RateLimitHttp.policyField(policy));
    }

    @Test
    void roundsTheSuccessfulWindowUpToTheNextToken() {
        RateLimitDecision decision = new RateLimitDecision(true, 99, 100, Duration.ZERO);
        assertEquals("\"default\";r=99;t=1", RateLimitHttp.limitField(policy, decision));
    }

    @Test
    void matchesRetryAfterToTheRejectionWindow() {
        RateLimitDecision decision = new RateLimitDecision(false, 0, 100, Duration.ofSeconds(10));
        assertEquals("\"default\";r=0;t=10", RateLimitHttp.limitField(policy, decision));
        assertEquals(10, RateLimitHttp.retryAfterSeconds(decision.retryAfter()));
    }

    @Test
    void roundsASubSecondWaitUpToOneSecond() {
        RateLimitDecision decision = new RateLimitDecision(false, 0, 100, Duration.ofMillis(1));
        assertEquals(1, RateLimitHttp.retryAfterSeconds(decision.retryAfter()));
        assertEquals(1, RateLimitHttp.effectiveWindowSeconds(policy, decision));
    }

    @Test
    void roundsTheStoreRetryAfterUpToAtLeastOneSecond() {
        assertEquals(1, RateLimitHttp.retryAfterSeconds(Duration.ofMillis(200)));
        assertEquals(1, RateLimitHttp.retryAfterSeconds(Duration.ofSeconds(1)));
        assertEquals(2, RateLimitHttp.retryAfterSeconds(Duration.ofMillis(1500)));
    }

    @Test
    void escapesPolicyNamesInTheStructuredField() {
        RateLimitPolicy named = new RateLimitPolicy(
                "say \"hi\"", List.of("/api/**"), 1, 1, Duration.ofSeconds(1));
        assertEquals("\"say \\\"hi\\\"\";q=1;w=1", RateLimitHttp.policyField(named));
    }

    @Test
    void writesTheQuotaExceededProblem() {
        assertEquals(
                "{\"type\":\"https://iana.org/assignments/http-problem-types#quota-exceeded\","
                        + "\"title\":\"Request cannot be satisfied as assigned quota has been exceeded\","
                        + "\"status\":429,\"detail\":\"Rate limit exceeded\",\"violated-policies\":[\"default\"]}",
                RateLimitHttp.problemBody(
                        429,
                        RateLimitHttp.QUOTA_EXCEEDED_TYPE,
                        RateLimitHttp.QUOTA_EXCEEDED_TITLE,
                        RateLimitHttp.QUOTA_EXCEEDED_DETAIL,
                        "default"));
    }
}
