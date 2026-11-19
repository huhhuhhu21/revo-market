
package com.revo.backend.common.exception;

import com.revo.backend.auth.dto.RegisterRequest;
import com.revo.backend.common.web.RequestIdFilter;

import jakarta.validation.Valid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MockMvcErrorHandlingTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturn400WhenRegistrationDataIsInvalid()
            throws Exception {

        String body = """
                {
                  "fullName": "",
                  "contactType": "EMAIL",
                  "contact": "abc@example.com",
                  "password": "123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()")
                        .value(2))
                .andExpect(jsonPath("$.error.details[*].field",
                        hasItems("fullName", "password")))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void shouldReturn409WhenListingIsLocked()
            throws Exception {

        ResultActions actions = mockMvc.perform(
                        patch("/api/v1/listings/125"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("LISTING_LOCKED"))
                .andExpect(header().exists("X-Request-ID"));

        String requestId = actions.andReturn()
                .getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(requestId);

        actions.andExpect(
                jsonPath("$.error.requestId").value(requestId)
        );
    }

    @RestController
    static class TestController {

        @PostMapping("/api/v1/auth/register")
        public void register(
                @Valid @RequestBody RegisterRequest request
        ) {
            // Chỉ dùng kiểm thử Validation.
        }

        @PatchMapping("/api/v1/listings/{listingId}")
        public void updateListing(
                @PathVariable Long listingId
        ) {
            throw new BusinessException(ErrorCode.LISTING_LOCKED);
        }
    }
}
