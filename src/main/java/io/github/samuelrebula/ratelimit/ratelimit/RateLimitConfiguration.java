package io.github.samuelrebula.ratelimit.ratelimit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    PolicyResolver policyResolver(RateLimitProperties properties) {
        List<RateLimitPolicy> policies = properties.policies().stream()
                .map(policy -> new RateLimitPolicy(
                        policy.name(),
                        policy.patterns(),
                        policy.capacity(),
                        policy.refillTokens(),
                        policy.refillPeriod()))
                .toList();
        return new PolicyResolver(policies);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage", havingValue = "redis")
    RedisRateLimitConnection redisRateLimitConnection(
            DataRedisConnectionDetails details,
            RateLimitProperties properties
    ) {
        if (details.getSslBundle() != null) {
            throw new IllegalStateException("TLS Redis connections are not supported by the rate limiter");
        }
        DataRedisConnectionDetails.Standalone standalone = details.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("rate-limit storage redis requires a standalone Redis connection");
        }
        return RedisRateLimitConnection.open(
                standalone.getHost(),
                standalone.getPort(),
                standalone.getDatabase(),
                details.getUsername(),
                details.getPassword(),
                properties.redis().commandTimeout(),
                properties.redis().keyExpirationMargin());
    }

    @Bean
    RateLimiter rateLimiter(RateLimitProperties properties, ObjectProvider<RedisRateLimitConnection> connections) {
        return create(properties, connections::getObject);
    }

    @Bean
    RateLimitFilter rateLimitFilter(PolicyResolver policyResolver, RateLimiter rateLimiter) {
        return new RateLimitFilter(policyResolver, rateLimiter);
    }

    static RateLimiter create(RateLimitProperties properties, Supplier<RedisRateLimitConnection> connections) {
        return switch (properties.storage()) {
            case "local" -> new LocalRateLimiter();
            case "redis" -> connections.get().rateLimiter();
            default -> throw new IllegalStateException(
                    "Unsupported rate-limit.storage '" + properties.storage() + "'. Supported values are 'local' and 'redis'.");
        };
    }
}
