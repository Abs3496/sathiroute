package com.routemesh.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routemesh.shared.web.ProblemWriter;
import com.routemesh.shared.limits.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

/**
 * Three kinds of caller. Driver devices (X-Device-Token) on the driver endpoints; anyone with a tracking token on
 * /v1/track; role-bearing JWTs on everything else under /v1. Trusted operator subject lists supplement signed claims.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    /** Driver app endpoints: pings, status events, proof of delivery, today's route. */
    static final String DEVICE_PATHS =
            "^/v1/(drivers/[^/]+/(pings:batch|events:batch|route)|shipments/[^/]+/(events|pod))$";

    @Bean
    @Order(1)
    SecurityFilterChain devices(HttpSecurity http, DeviceTokens tokens, ObjectMapper json) throws Exception {
        http.securityMatcher(new RegexRequestMatcher(DEVICE_PATHS, null))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new DeviceAuth(tokens, json), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("DEVICE"));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http, ObjectMapper json, RateLimiter limiter,
                            @Value("${routemesh.security.api-requests-per-minute:300}") int apiRequestsPerMinute) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/favicon.ico").permitAll()
                        .requestMatchers("/dev/session").permitAll()
                        .requestMatchers("/dev/demo/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/track/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/track/*/reschedule", "/v1/track/*/otp:resend").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new JwtRoles()))
                        .authenticationEntryPoint((req, res, ex) -> ProblemWriter.write(res, json,
                                HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> ProblemWriter.write(res, json,
                                HttpStatus.UNAUTHORIZED, "unauthenticated", "A valid bearer token is required"))
                        .accessDeniedHandler((req, res, ex) -> ProblemWriter.write(res, json,
                                HttpStatus.FORBIDDEN, "forbidden", "Not allowed")));
        http.addFilterAfter(new ApiSecurityFilter(limiter, json, apiRequestsPerMinute),
                BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
