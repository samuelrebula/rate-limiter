package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=local",
        "rate-limit.policies[0].name=expensive",
        "rate-limit.policies[0].patterns[0]=/api/expensive",
        "rate-limit.policies[0].capacity=1",
        "rate-limit.policies[0].refill-tokens=1",
        "rate-limit.policies[0].refill-period=1m",
        "rate-limit.policies[1].name=default",
        "rate-limit.policies[1].patterns[0]=/api/**",
        "rate-limit.policies[1].capacity=2",
        "rate-limit.policies[1].refill-tokens=2",
        "rate-limit.policies[1].refill-period=1m"
})
@AutoConfigureMockMvc
class DifferentPoliciesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exhaustingOneEndpointDoesNotExhaustTheOther() throws Exception {
        String consumer = "203.0.113.60";

        mockMvc.perform(get("/api/expensive").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/expensive").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void helloLimitDoesNotConsumeTheExpensivePolicy() throws Exception {
        String consumer = "203.0.113.61";

        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/expensive").with(remoteAddress(consumer)))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
