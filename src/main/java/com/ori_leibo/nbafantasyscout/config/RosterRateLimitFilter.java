package com.ori_leibo.nbafantasyscout.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits roster checks per visitor. A Yahoo league check makes ~9 Yahoo calls through one shared rate limiter, so
 * without this a single client sending many requests would make everyone else wait (or get the server blocked).
 */
public class RosterRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RosterRateLimitFilter.class);

    private final ClientRequestLimit limit;
    private final int perMinute;

    public RosterRateLimitFilter(ClientRequestLimit limit, int perMinute) {
        this.limit = limit;
        this.perMinute = perMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/roster"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = clientOf(request);
        ClientRequestLimit.Decision decision = limit.tryAcquire(client);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Roster check limit reached for {}", client);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        // same shape as the other errors, so the page shows the detail like any other message
        response.getWriter().write("""
                {"status":429,"title":"Too Many Requests","detail":"Up to %d roster checks a minute. Try again in %d seconds."}"""
                .formatted(perMinute, decision.retryAfterSeconds()));
    }

    /**
     * The visitor's address behind the host's proxies: Cloudflare's {@code CF-Connecting-IP} first (set at the edge,
     * a client can't override it there), then the first {@code X-Forwarded-For} entry, then the socket.
     */
    static String clientOf(HttpServletRequest request) {
        String cloudflare = request.getHeader("CF-Connecting-IP");
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare.strip();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
