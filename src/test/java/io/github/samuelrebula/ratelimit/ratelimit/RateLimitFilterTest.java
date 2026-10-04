package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(header().string("RateLimit-Policy", "\"default\";q=1;w=60"))
                .andExpect(header().string("RateLimit", "\"default\";r=0;t=60"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void rejectsExcessRequestWith429() throws Exception {
        String consumer = "203.0.113.20";
        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").with(remoteAddress(consumer)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(RateLimitHttp.QUOTA_EXCEEDED_TYPE))
                .andExpect(jsonPath("$.title").value(RateLimitHttp.QUOTA_EXCEEDED_TITLE))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.violated-policies[0]").value("default"))
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(header().string("RateLimit-Policy", "\"default\";q=1;w=60"))
                .andExpect(header().string("RateLimit", "\"default\";r=0;t=60"));
    }

    @Test
    void limitsEachRemoteAddressSeparately() throws Exception {
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.30")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.31")))
                .andExpect(status().isOk());
    }

    @Test
    void normalizesEquivalentAddressesIntoOneBucket() throws Exception {
        mockMvc.perform(get("/api/hello").with(remoteAddress("::ffff:203.0.113.45")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress("203.0.113.45")))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/hello").with(remoteAddress("[2001:DB8::1]:443")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress("2001:db8::1")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void ignoresForwardedHeaderWhenTrustIsDisabled() throws Exception {
        String remote = "203.0.113.46";
        mockMvc.perform(get("/api/hello").with(remoteAddress(remote)).header("X-Forwarded-For", "198.51.100.10"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress(remote)).header("X-Forwarded-For", "198.51.100.11"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sharesTheUnknownBucketWhenTheAddressIsBlank() throws Exception {
        mockMvc.perform(get("/api/hello").with(remoteAddress("")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress("   ")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void doesNotLimitPathsOutsideConfiguredPolicies() throws Exception {
        mockMvc.perform(get("/not-limited").with(remoteAddress("203.0.113.40")))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("RateLimit"))
                .andExpect(header().doesNotExist("RateLimit-Policy"));
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
