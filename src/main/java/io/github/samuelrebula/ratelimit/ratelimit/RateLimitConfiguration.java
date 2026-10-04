package io.github.samuelrebula.ratelimit.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfiguration.class);

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
        PolicyResolver resolver = new PolicyResolver(policies);
        if (policies.isEmpty()) {
            log.info("Loaded no rate limit policies");
        } else {
            for (RateLimitPolicy policy : policies) {
                log.info(
                        "Loaded rate limit policy {} capacity {} refill {} per {} for {}",
                        policy.name(),
                        policy.capacity(),
                        policy.refillTokens(),
                        policy.refillPeriod(),
                        policy.patterns());
            }
        }
        return resolver;
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
    ClientIpConsumerKeyResolver consumerKeyResolver() {
        return new ClientIpConsumerKeyResolver();
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.trust-forwarded-headers", havingValue = "true")
    FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage", havingValue = "redis")
    RateLimitStoreHealthIndicator rateLimitStoreHealthIndicator(RedisRateLimitConnection connection) {
        return new RateLimitStoreHealthIndicator(connection);
    }

    @Bean
    RateLimitMetrics rateLimitMetrics(MeterRegistry registry) {
        return new RateLimitMetrics(registry);
    }

    @Bean
    RateLimitFilter rateLimitFilter(
            PolicyResolver policyResolver,
            RateLimiter rateLimiter,
            ClientIpConsumerKeyResolver consumerKeys,
            RateLimitProperties properties,
            RateLimitMetrics metrics
    ) {
        return new RateLimitFilter(
                policyResolver,
                rateLimiter,
                consumerKeys,
                properties.redis().commandTimeout(),
                metrics);
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
