package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyResolverTest {

    private final RateLimitPolicy expensive = policy("expensive", "/api/expensive");
    private final RateLimitPolicy fallback = policy("default", "/api/**");
    private final PolicyResolver resolver = new PolicyResolver(List.of(expensive, fallback));

    @Test
    void firstMatchingPatternWins() {
        assertThat(resolver.resolve("/api/expensive")).contains(expensive);
        assertThat(resolver.resolve("/api/hello")).contains(fallback);
    }

    @Test
    void unmatchedPathIsNotLimited() {
        assertThat(resolver.resolve("/not-limited")).isEmpty();
    }

    @Test
    void rejectsDuplicatePolicyNames() {
        assertThatThrownBy(() -> new PolicyResolver(List.of(expensive, policy("expensive", "/other"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expensive");
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new RateLimitPolicy("default", List.of("/api/**"), 0, 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    private static RateLimitPolicy policy(String name, String pattern) {
        return new RateLimitPolicy(name, List.of(pattern), 10, 10, Duration.ofMinutes(1));
    }
}
