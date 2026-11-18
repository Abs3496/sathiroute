package com.routemesh.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routemesh.shared.web.ProblemWriter;
import com.routemesh.shared.limits.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Per-subject API rate limit and audit trail for authenticated write requests. */
final class ApiSecurityFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("routemesh.security.audit");
    private final RateLimiter limiter;
    private final ObjectMapper json;
    private final int perMinute;

    ApiSecurityFilter(RateLimiter limiter, ObjectMapper json,
                      @Value("${routemesh.security.api-requests-per-minute:300}") int perMinute) {
        this.limiter = limiter;
        this.json = json;
        this.perMinute = perMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }
        String subject = jwt.getSubject();
        String role = authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).findFirst().orElse("ROLE_UNKNOWN");
        if (!limiter.allow("api:" + sha256(subject), perMinute, Duration.ofMinutes(1))) {
            response.setHeader("Retry-After", "60");
            ProblemWriter.write(response, json, HttpStatus.TOO_MANY_REQUESTS,
                    "rate-limit-exceeded", "API request rate limit exceeded");
            audit.warn("action=rate_limited subject_hash={} role={} method={} path={}", sha256(subject), role,
                    request.getMethod(), request.getRequestURI());
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) {
                audit.info("subject_hash={} role={} method={} path={} status={}", sha256(subject), role,
                        request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
