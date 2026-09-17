package com.texto.emailplatform.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RateLimitStubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rateLimitExceededReturns429RetryAfterAndStableCode() throws Exception {
        mockMvc.perform(get("/limited").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value("Email send rate limit exceeded. Try again later."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("redis"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rate-limit:"))));
    }

    @RestController
    static class RateLimitStubController {

        @GetMapping("/limited")
        void limited() {
            throw new ApiException(
                    429,
                    "EMAIL_RATE_LIMIT_EXCEEDED",
                    "Email send rate limit exceeded. Try again later.",
                    17
            );
        }
    }
}
