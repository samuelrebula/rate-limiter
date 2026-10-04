package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=local",
        "rate-limit.policies[0].name=default",
        "rate-limit.policies[0].patterns[0]=/api/**",
        "rate-limit.policies[0].capacity=2",
        "rate-limit.policies[0].refill-tokens=2",
        "rate-limit.policies[0].refill-period=1m"
})
@AutoConfigureMockMvc
class RateLimitHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void remainingDecreasesUntilRetryAfterMatchesTheWindow() throws Exception {
        String policy = "\"default\";q=2;w=60";
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.90")))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit-Policy", policy))
                .andExpect(header().string("RateLimit", "\"default\";r=1;t=30"))
                .andExpect(header().doesNotExist("Retry-After"));

        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.90")))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit", "\"default\";r=0;t=30"))
                .andExpect(header().doesNotExist("Retry-After"));

        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.90")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Policy", policy))
                .andExpect(header().string("RateLimit", "\"default\";r=0;t=30"))
                .andExpect(header().string("Retry-After", "30"));
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
