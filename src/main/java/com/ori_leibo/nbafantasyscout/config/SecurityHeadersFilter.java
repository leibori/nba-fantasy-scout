package com.ori_leibo.nbafantasyscout.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Standard browser security headers on every response. */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    // everything from this site only; inline script and style are needed by the one-file page and by Swagger UI
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline'",
            // Yahoo team logos in the league team picker
            "img-src 'self' data: https://s.yimg.com https://yahoofantasysports-res.cloudinary.com",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY"); // no framing: older browsers ignore frame-ancestors
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // browsers honour this only over HTTPS, so it is harmless on a local http run
        response.setHeader("Strict-Transport-Security", "max-age=31536000");
        chain.doFilter(request, response);
    }
}
