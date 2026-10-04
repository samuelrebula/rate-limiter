package io.github.samuelrebula.ratelimit.ratelimit;

import org.springframework.util.AntPathMatcher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PolicyResolver {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<RateLimitPolicy> policies;

    public PolicyResolver(List<RateLimitPolicy> policies) {
        Set<String> names = new HashSet<>();
        for (RateLimitPolicy policy : policies) {
            if (!names.add(policy.name())) {
                throw new IllegalArgumentException("Duplicate rate-limit policy '" + policy.name() + "'");
            }
        }
        this.policies = List.copyOf(policies);
    }

    public Optional<RateLimitPolicy> resolve(String path) {
        for (RateLimitPolicy policy : policies) {
            for (String pattern : policy.patterns()) {
                if (matcher.match(pattern, path)) {
                    return Optional.of(policy);
                }
            }
        }
        return Optional.empty();
    }
}
