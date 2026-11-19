
package com.revo.backend.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    @Test
    void shouldCreateRequestIdAndClearMdc() throws Exception {

        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/categories");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                new MockFilterChain()
        );

        String requestId = response.getHeader("X-Request-ID");

        assertNotNull(requestId);
        assertTrue(requestId.startsWith("req-"));

        assertEquals(
                requestId,
                request.getAttribute("requestId")
        );

        assertNull(MDC.get("requestId"));
    }
}
