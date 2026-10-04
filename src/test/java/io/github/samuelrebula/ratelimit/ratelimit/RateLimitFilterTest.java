package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=local",
        "rate-limit.policies[0].name=default",
        "rate-limit.policies[0].patterns[0]=/api/**",
        "rate-limit.policies[0].capacity=1",
        "rate-limit.policies[0].refill-tokens=1",
        "rate-limit.policies[0].refill-period=1m"
})
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsRequestWithinLimit() throws Exception {
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ok"));
    }

    @Test
    void rejectsExcessRequestWith429() throws Exception {
        String consumer = "203.0.113.20";
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.detail").value("Rate limit exceeded"));
    }

    @Test
    void limitsEachRemoteAddressSeparately() throws Exception {
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.30")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.31")))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotLimitPathsOutsideConfiguredPolicies() throws Exception {
        mockMvc.perform(get("/not-limited").with(remoteAddress("203.0.113.40")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/not-limited").with(remoteAddress("203.0.113.40")))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
