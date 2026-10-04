package io.github.samuelrebula.ratelimit.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "rate-limit.storage=local")
@AutoConfigureMockMvc
class RateLimitStoreFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    void storeFailurePropagatesAsServerError() throws Exception {
        when(rateLimiter.consume(any(), any()))
                .thenThrow(new RateLimitStoreException("redis down", new IllegalStateException("connection refused")));

        mockMvc.perform(get("/api/hello").with(request -> {
                    request.setRemoteAddr("203.0.113.81");
                    return request;
                }))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Rate limit store unavailable"));
    }
}
