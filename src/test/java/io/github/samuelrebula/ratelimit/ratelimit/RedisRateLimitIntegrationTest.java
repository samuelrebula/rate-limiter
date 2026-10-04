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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=redis",
        "rate-limit.redis.command-timeout=1s",
        "rate-limit.redis.key-expiration-margin=10s",
        "rate-limit.policies[0].name=http",
        "rate-limit.policies[0].patterns[0]=/api/**",
        "rate-limit.policies[0].capacity=1",
        "rate-limit.policies[0].refill-tokens=1",
        "rate-limit.policies[0].refill-period=1h"
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

    @Test
    void unreachableRedisFailsAsStoreError() {
        RateLimitPolicy policy = new RateLimitPolicy("down", List.of("/api/**"), 1, 1, Duration.ofMinutes(1));

        assertThatThrownBy(() -> {
            try (RedisRateLimitConnection connection = RedisRateLimitConnection.open(
                    "127.0.0.1",
                    1,
                    0,
                    null,
                    null,
                    Duration.ofMillis(200),
                    KEY_EXPIRATION_MARGIN
            )) {
                connection.rateLimiter().consume(policy, "203.0.113.90");
            }
        }).isInstanceOf(RateLimitStoreException.class);
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
