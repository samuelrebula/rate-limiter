package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=redis",
        "rate-limit.redis.command-timeout=1s",
        "rate-limit.redis.key-expiration-margin=10s",
        "rate-limit.policies[0].name=expensive",
        "rate-limit.policies[0].patterns[0]=/api/expensive",
        "rate-limit.policies[0].capacity=1",
        "rate-limit.policies[0].refill-tokens=1",
        "rate-limit.policies[0].refill-period=1h",
        "rate-limit.policies[1].name=http",
        "rate-limit.policies[1].patterns[0]=/api/**",
        "rate-limit.policies[1].capacity=1",
        "rate-limit.policies[1].refill-tokens=1",
        "rate-limit.policies[1].refill-period=1h"
})
@AutoConfigureMockMvc
@Testcontainers
class RedisRateLimitIntegrationTest {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration KEY_EXPIRATION_MARGIN = Duration.ofSeconds(10);

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void httpAndAnotherInstanceShareTheSameLimit() throws Exception {
        String consumer = "203.0.113.70";
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());

        try (RedisRateLimitConnection other = open()) {
            RateLimitPolicy policy = new RateLimitPolicy("http", List.of("/api/**"), 1, 1, Duration.ofHours(1));
            assertThat(other.rateLimiter().consume(policy, consumer).allowed()).isFalse();
            assertThat(other.rateLimiter().consume(policy, "203.0.113.71").allowed()).isTrue();
        }
    }

    @Test
    void endpointsKeepIndependentBucketsSharedAcrossConnections() throws Exception {
        String consumer = "203.0.113.73";
        mockMvc.perform(get("/api/expensive").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/expensive").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Policy", "\"expensive\";q=1;w=3600"))
                .andExpect(header().string("RateLimit", "\"expensive\";r=0;t=3600"))
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.detail").value("Rate limit exceeded"));

        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit-Policy", "\"http\";q=1;w=3600"));
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests());

        RateLimitPolicy expensive = new RateLimitPolicy("expensive", List.of("/api/expensive"), 1, 1, Duration.ofHours(1));
        RateLimitPolicy http = new RateLimitPolicy("http", List.of("/api/**"), 1, 1, Duration.ofHours(1));
        try (RedisRateLimitConnection other = open()) {
            RedisRateLimiter limiter = other.rateLimiter();
            assertThat(limiter.consume(expensive, consumer).allowed()).isFalse();
            assertThat(limiter.consume(http, consumer).allowed()).isFalse();
            assertThat(limiter.consume(expensive, "203.0.113.74").allowed()).isTrue();
        }
    }

    @Test
    void concurrentConsumersOnTwoConnectionsDoNotExceedCapacity() throws Exception {
        RateLimitPolicy policy = new RateLimitPolicy("concurrent", List.of("/api/**"), 10, 1, Duration.ofHours(1));
        int threads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try (RedisRateLimitConnection first = open();
             RedisRateLimitConnection second = open()) {
            RedisRateLimiter firstLimiter = first.rateLimiter();
            RedisRateLimiter secondLimiter = second.rateLimiter();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                RedisRateLimiter limiter = i % 2 == 0 ? firstLimiter : secondLimiter;
                results.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to start");
                    }
                    return limiter.consume(policy, "203.0.113.80").allowed();
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(policy.capacity());
        } finally {
            executor.shutdownNow();
        }
    }

    private static RedisRateLimitConnection open() {
        return RedisRateLimitConnection.open(
                REDIS.getHost(),
                REDIS.getMappedPort(6379),
                0,
                null,
                null,
                COMMAND_TIMEOUT,
                KEY_EXPIRATION_MARGIN);
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
