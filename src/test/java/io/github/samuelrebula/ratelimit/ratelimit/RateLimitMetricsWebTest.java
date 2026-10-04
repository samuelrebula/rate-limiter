package io.github.samuelrebula.ratelimit.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.storage=local",
        "rate-limit.policies[0].name=observed",
        "rate-limit.policies[0].patterns[0]=/api/**",
        "rate-limit.policies[0].capacity=1",
        "rate-limit.policies[0].refill-tokens=1",
        "rate-limit.policies[0].refill-period=1m"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RateLimitMetricsWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registry;

    @Test
    void recordsDecisionsWithoutTheClientAddress(CapturedOutput output) throws Exception {
        String client = "203.0.113.77";
        double requestsBefore = count(RateLimitMetrics.REQUESTS);
        mockMvc.perform(get("/not-limited").with(remoteAddress(client)))
                .andExpect(status().isNotFound());
        assertThat(count(RateLimitMetrics.REQUESTS)).isEqualTo(requestsBefore);

        mockMvc.perform(get("/api/hello").with(remoteAddress(client)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello").with(remoteAddress(client)))
                .andExpect(status().isTooManyRequests());

        assertThat(count(RateLimitMetrics.REQUESTS, "policy", "observed", "result", "allowed")).isEqualTo(1);
        assertThat(count(RateLimitMetrics.REQUESTS, "policy", "observed", "result", "rejected")).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.CONSUME).tag("policy", "observed").timer().count()).isEqualTo(2);
        assertThat(registry.find(RateLimitMetrics.REQUESTS).counters()).allSatisfy(counter -> {
            assertThat(counter.getId().getTags()).extracting(Tag::getKey).containsExactlyInAnyOrder("policy", "result");
            assertThat(counter.getId().getTags()).extracting(Tag::getValue).doesNotContain(client);
        });
        assertThat(output).contains("Loaded rate limit policy observed");
        assertThat(output).doesNotContain(client);

        mockMvc.perform(get("/actuator/metrics/" + RateLimitMetrics.REQUESTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(RateLimitMetrics.REQUESTS));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    private double count(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
