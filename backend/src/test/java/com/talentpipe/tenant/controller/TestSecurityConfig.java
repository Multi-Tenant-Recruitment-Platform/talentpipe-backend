package com.talentpipe.tenant.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Minimal Spring Security configuration for {@code @WebMvcTest} controller
 * slice tests in the {@code tenant.controller} package.
 *
 * <p>The full {@link com.talentpipe.security.SecurityConfig} cannot be loaded
 * in a web slice test because it depends on beans ({@code JwtTokenProvider},
 * {@code SecurityErrorWriter}, etc.) that are not in scope. This lightweight
 * configuration provides exactly what the controller tests need:</p>
 * <ul>
 *   <li>{@code @EnableMethodSecurity} — activates {@code @PreAuthorize} on
 *       controller methods so role-based access control is exercised.</li>
 *   <li>Stateless session management — no session is created.</li>
 *   <li>Public permit-all for {@code /api/v1/public/**} — mirrors the
 *       production rule so {@code PublicCompanyControllerTest} is not blocked.</li>
 *   <li>All other requests require authentication; unauthenticated requests
 *       return 401 (not the Spring Security default 403) via a custom entry
 *       point — this mirrors the production {@code RestAuthenticationEntryPoint}
 *       behaviour.</li>
 * </ul>
 */
@TestConfiguration
@EnableMethodSecurity
class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // Return 401 for unauthenticated requests — matches
                        // RestAuthenticationEntryPoint in production.
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        // Return 403 for authenticated-but-unauthorized requests.
                        .accessDeniedHandler(forbiddenHandler()));
        return http.build();
    }

    private static AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized");
    }

    private static AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) ->
                response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden");
    }
}
