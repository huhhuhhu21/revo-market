
package com.revo.backend.common.security;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class RestSecurityErrorHandlerTest {

    private final RestSecurityErrorHandler handler =
            new RestSecurityErrorHandler(
                    JsonMapper.builder().build()
            );

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setAttribute("requestId", "req-test-401");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.commence(
                request,
                response,
                new InsufficientAuthenticationException("Test")
        );

        assertEquals(401, response.getStatus());
        assertEquals(
                "req-test-401",
                response.getHeader("X-Request-ID")
        );
        assertTrue(
                response.getContentAsString()
                        .contains("\"code\":\"UNAUTHORIZED\"")
        );
    }

    @Test
    void shouldReturn403WhenAccessDenied() throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setAttribute("requestId", "req-test-403");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        handler.handle(
                request,
                response,
                new AccessDeniedException("Test")
        );

        assertEquals(403, response.getStatus());
        assertTrue(
                response.getContentAsString()
                        .contains("\"code\":\"FORBIDDEN\"")
        );
    }
}
