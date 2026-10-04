package io.github.samuelrebula.ratelimit.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String REJECTED_BODY = "{\"detail\":\"Rate limit exceeded\"}";
    private static final String STORE_FAILURE_BODY = "{\"detail\":\"Rate limit store unavailable\"}";

    private final PolicyResolver policies;
    private final RateLimiter rateLimiter;

    public RateLimitFilter(PolicyResolver policies, RateLimiter rateLimiter) {
        this.policies = policies;
        this.rateLimiter = rateLimiter;
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

        RateLimitDecision decision;
        try {
            decision = rateLimiter.consume(policy.get(), consumerKey(request));
        } catch (RateLimitStoreException exception) {
            log.error("Rate limit store is unavailable", exception);
            reject(response, HttpStatus.INTERNAL_SERVER_ERROR, STORE_FAILURE_BODY);
            return;
        }
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        reject(response, HttpStatus.TOO_MANY_REQUESTS, REJECTED_BODY);
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String body) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private static String consumerKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        return remoteAddress;
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
