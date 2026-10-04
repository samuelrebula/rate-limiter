package io.github.samuelrebula.ratelimit.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=local",
        "rate-limit.redis.command-timeout=1s"
})
@AutoConfigureMockMvc
class RateLimitStoreFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    void storeFailureStopsTheRequestWith503() throws Exception {
        when(rateLimiter.consume(any(), any()))
                .thenThrow(new RateLimitStoreException("redis down", new IllegalStateException("connection refused")));

        mockMvc.perform(get("/api/hello").with(request -> {
                    request.setRemoteAddr("203.0.113.81");
                    return request;
                }))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("Rate limit store unavailable"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(header().doesNotExist("RateLimit"))
                .andExpect(header().doesNotExist("RateLimit-Policy"));

        assertThat(registry.get(RateLimitMetrics.STORE_ERRORS).counter().count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.STORE_ERRORS).counter().getId().getTags()).isEmpty();
        assertThat(registry.find(RateLimitMetrics.REQUESTS).counters()).isEmpty();
        assertThat(registry.get(RateLimitMetrics.CONSUME).tag("policy", "default").timer().count()).isEqualTo(1);
    }

    @Test
    void storeFailureDoesNotApplyOutsideConfiguredPolicies() throws Exception {
        when(rateLimiter.consume(any(), any()))
                .thenThrow(new RateLimitStoreException("redis down", new IllegalStateException("connection refused")));

        mockMvc.perform(get("/not-limited").with(request -> {
                    request.setRemoteAddr("203.0.113.82");
                    return request;
                }))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Retry-After"));
    }
}
