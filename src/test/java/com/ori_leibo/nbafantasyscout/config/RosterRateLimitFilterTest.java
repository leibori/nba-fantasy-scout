package com.ori_leibo.nbafantasyscout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RosterRateLimitFilterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);

    private final RosterRateLimitFilter filter =
            new RosterRateLimitFilter(new ClientRequestLimit(2, Duration.ofMinutes(1), CLOCK), 2);

    private MockHttpServletResponse send(String method, String path, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // a request that went through reached the chain; a limited one did not
        assertEquals(response.getStatus() != 429, chain.getRequest() != null);
        return response;
    }

    @Test
    void theThirdRosterCheckInAMinuteGetsA429ThatThePageCanShow() throws Exception {
        // reading lists is not limited and does not use up roster checks
        for (int i = 0; i < 5; i++) {
            assertEquals(200, send("GET", "/api/v1/recommendations/hot", "1.1.1.1").getStatus());
        }
        assertEquals(200, send("POST", "/api/v1/roster", "1.1.1.1").getStatus());
        assertEquals(200, send("POST", "/api/v1/roster/from-yahoo", "1.1.1.1").getStatus());

        MockHttpServletResponse limited = send("POST", "/api/v1/roster", "1.1.1.1, 10.0.0.1");
        assertEquals(429, limited.getStatus());
        assertEquals("60", limited.getHeader("Retry-After"));
        assertTrue(limited.getContentAsString().contains("\"detail\":\"Up to 2 roster checks a minute. Try again in 60 seconds.\""));

        assertEquals(200, send("POST", "/api/v1/roster", "2.2.2.2").getStatus());
    }

    @Test
    void theVisitorIsTakenFromTheEdgeHeaderThenTheForwardedChainThenTheSocket() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/roster");
        request.setRemoteAddr("10.0.0.9");
        assertEquals("10.0.0.9", RosterRateLimitFilter.clientOf(request));
        request.addHeader("X-Forwarded-For", "3.3.3.3, 10.0.0.1");
        assertEquals("3.3.3.3", RosterRateLimitFilter.clientOf(request));
        request.addHeader("CF-Connecting-IP", "4.4.4.4");
        assertEquals("4.4.4.4", RosterRateLimitFilter.clientOf(request));
    }
}
