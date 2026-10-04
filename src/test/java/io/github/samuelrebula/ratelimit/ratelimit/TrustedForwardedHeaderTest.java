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
        "rate-limit.trust-forwarded-headers=true",
        "rate-limit.policies[0].name=default",
        "rate-limit.policies[0].patterns[0]=/api/**",
        "rate-limit.policies[0].capacity=1",
        "rate-limit.policies[0].refill-tokens=1",
        "rate-limit.policies[0].refill-period=1m"
})
@AutoConfigureMockMvc
class TrustedForwardedHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usesTheForwardedClientAddressWhenTrustIsEnabled() throws Exception {
        mockMvc.perform(get("/api/hello")
                        .with(remoteAddress("10.0.0.8"))
                        .header("X-Forwarded-For", "203.0.113.50"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello")
                        .with(remoteAddress("10.0.0.9"))
                        .header("X-Forwarded-For", "203.0.113.50"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/hello")
                        .with(remoteAddress("10.0.0.8"))
                        .header("X-Forwarded-For", "203.0.113.51"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
