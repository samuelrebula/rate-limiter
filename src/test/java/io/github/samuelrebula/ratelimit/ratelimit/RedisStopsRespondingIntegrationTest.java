package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=redis",
        "rate-limit.redis.command-timeout=1s",
        "rate-limit.redis.key-expiration-margin=10s",
        "rate-limit.policies[0].name=http",
        "rate-limit.policies[0].patterns[0]=/api/**",
        "rate-limit.policies[0].capacity=5",
        "rate-limit.policies[0].refill-tokens=5",
        "rate-limit.policies[0].refill-period=1h"
})
@AutoConfigureMockMvc
@Testcontainers
class RedisStopsRespondingIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void stoppedRedisRejectsLimitedRequestsAndLeavesOtherPathsAlone() throws Exception {
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.90")))
                .andExpect(status().isOk());

        REDIS.stop();

        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.90")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("Rate limit store unavailable"))
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(header().doesNotExist("RateLimit"));

        mockMvc.perform(get("/not-limited").with(remoteAddress("203.0.113.90")))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Retry-After"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
