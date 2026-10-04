package io.github.samuelrebula.ratelimit.ratelimit;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Writes the quota response from
 * <a href="https://www.ietf.org/archive/id/draft-ietf-httpapi-ratelimit-headers-11.html">draft-ietf-httpapi-ratelimit-headers-11</a>.
 *
 * <p>{@code RateLimit-Policy} advertises {@code q} as the bucket capacity and {@code w} as the
 * refill period in whole seconds. The shipped policies refill {@code q} tokens over that period.
 * {@code RateLimit} advertises {@code r} as the tokens left after this request. On success,
 * {@code t} is the number of seconds until one more token can arrive, rounded up. On a rejection,
 * {@code t} equals {@code Retry-After}. The partition key is the client address and is not echoed.
 *
 * <p>A store failure is not a quota result. The request stops and the response is
 * {@code 503} with {@code Retry-After} set to the Redis command timeout, in whole seconds.
 */
final class RateLimitHttp {

    static final String QUOTA_EXCEEDED_TYPE =
            "https://iana.org/assignments/http-problem-types#quota-exceeded";
    static final String QUOTA_EXCEEDED_TITLE =
            "Request cannot be satisfied as assigned quota has been exceeded";
    static final String QUOTA_EXCEEDED_DETAIL = "Rate limit exceeded";
    static final String STORE_UNAVAILABLE_DETAIL = "Rate limit store unavailable";

    private RateLimitHttp() {
    }

    static void writeQuota(HttpServletResponse response, RateLimitPolicy policy, RateLimitDecision decision) {
        response.setHeader("RateLimit-Policy", policyField(policy));
        response.setHeader("RateLimit", limitField(policy, decision));
    }

    static void writeRejection(HttpServletResponse response, RateLimitPolicy policy, RateLimitDecision decision)
            throws IOException {
        writeQuota(response, policy, decision);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(decision.retryAfter())));
        writeProblem(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                QUOTA_EXCEEDED_TYPE,
                QUOTA_EXCEEDED_TITLE,
                QUOTA_EXCEEDED_DETAIL,
                policy.name());
    }

    static void writeStoreUnavailable(HttpServletResponse response, Duration commandTimeout) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(commandTimeout)));
        writeProblem(
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                "about:blank",
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                STORE_UNAVAILABLE_DETAIL,
                null);
    }

    static String policyField(RateLimitPolicy policy) {
        return "\"" + escapeStructuredString(policy.name()) + "\";q=" + policy.capacity()
                + ";w=" + windowSeconds(policy.refillPeriod());
    }

    static String limitField(RateLimitPolicy policy, RateLimitDecision decision) {
        return "\"" + escapeStructuredString(policy.name()) + "\";r=" + decision.remainingTokens()
                + ";t=" + effectiveWindowSeconds(policy, decision);
    }

    static long retryAfterSeconds(Duration wait) {
        return Math.max(ceilSeconds(wait), 1);
    }

    static long effectiveWindowSeconds(RateLimitPolicy policy, RateLimitDecision decision) {
        if (!decision.allowed()) {
            return retryAfterSeconds(decision.retryAfter());
        }
        return Math.max(tokenIntervalSeconds(policy), 1);
    }

    private static long windowSeconds(Duration period) {
        return Math.max(ceilSeconds(period), 1);
    }

    private static long tokenIntervalSeconds(RateLimitPolicy policy) {
        long periodNanos = policy.refillPeriod().toNanos();
        long intervalNanos = (periodNanos + policy.refillTokens() - 1) / policy.refillTokens();
        return ceilSeconds(Duration.ofNanos(intervalNanos));
    }

    private static long ceilSeconds(Duration duration) {
        long seconds = duration.getSeconds();
        if (duration.getNano() > 0) {
            seconds = seconds == Long.MAX_VALUE ? Long.MAX_VALUE : seconds + 1;
        }
        return Math.max(seconds, 0);
    }

    private static void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String type,
            String title,
            String detail,
            String violatedPolicy
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(problemBody(status.value(), type, title, detail, violatedPolicy));
    }

    static String problemBody(int status, String type, String title, String detail, String violatedPolicy) {
        StringBuilder body = new StringBuilder();
        body.append("{\"type\":").append(jsonString(type));
        body.append(",\"title\":").append(jsonString(title));
        body.append(",\"status\":").append(status);
        body.append(",\"detail\":").append(jsonString(detail));
        if (violatedPolicy != null) {
            body.append(",\"violated-policies\":[").append(jsonString(violatedPolicy)).append(']');
        }
        body.append('}');
        return body.toString();
    }

    private static String escapeStructuredString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                default -> escaped.append(character);
            }
        }
        escaped.append('"');
        return escaped.toString();
    }
}
