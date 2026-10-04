package io.github.samuelrebula.ratelimit.ratelimit;

import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final PolicyResolver policies;
    private final RateLimiter rateLimiter;
    private final ClientIpConsumerKeyResolver consumerKeys;
    private final Duration storeRetryAfter;
    private final RateLimitMetrics metrics;

    public RateLimitFilter(
            PolicyResolver policies,
            RateLimiter rateLimiter,
            ClientIpConsumerKeyResolver consumerKeys,
            Duration storeRetryAfter,
            RateLimitMetrics metrics
    ) {
        this.policies = policies;
        this.rateLimiter = rateLimiter;
        this.consumerKeys = consumerKeys;
        this.storeRetryAfter = storeRetryAfter;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<RateLimitPolicy> policy = policies.resolve(pathWithinApplication(request));
        if (policy.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Timer.Sample sample = metrics.start();
        RateLimitDecision decision;
        try {
            decision = rateLimiter.consume(policy.get(), consumerKeys.resolve(request));
        } catch (RateLimitStoreException exception) {
            metrics.recordStoreError(sample, policy.get().name());
            log.error("Rate limit store is unavailable", exception);
            RateLimitHttp.writeStoreUnavailable(response, storeRetryAfter);
            return;
        }
        metrics.recordDecision(sample, policy.get().name(), decision.allowed());
        if (decision.allowed()) {
            RateLimitHttp.writeQuota(response, policy.get(), decision);
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitHttp.writeRejection(response, policy.get(), decision);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.isEmpty() ? "/" : path;
    }
}
