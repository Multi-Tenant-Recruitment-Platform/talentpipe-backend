package com.talentpipe.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless, token-based security configuration.
 *
 * <p>Filter order (both custom filters sit ahead of the stock username/password
 * filter, which is never used):</p>
 * <pre>
 *   TenantResolvingFilter  — binds the provisional tenant claim to TenantContext
 *   JwtAuthenticationFilter — verifies the JWT, populates the SecurityContext
 * </pre>
 *
 * <p>No HTTP session is ever created; CSRF is disabled because authentication
 * travels exclusively in the Authorization header (no cookies), which
 * cross-site requests cannot set.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** bcrypt cost factor mandated by the architecture doc. */
    private static final int BCRYPT_COST = 12;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   SecurityErrorWriter errorWriter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   @Value("${talentpipe.security.login-rate-limit.max-per-minute:10}")
                                                   int loginRateLimitPerMinute) throws Exception {
        // Instantiated here (not as @Component) so the servlet container does
        // not ALSO auto-register them outside the security chain.
        var jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, errorWriter);
        var tenantResolvingFilter = new TenantResolvingFilter(jwtTokenProvider);
        var loginRateLimitFilter = new LoginRateLimitFilter(errorWriter, loginRateLimitPerMinute);

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Credential and token-bearing flows: the token in the
                        // link IS the credential, so no session is required.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/accept-invite").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // Everything else needs a token; role scoping is enforced
                        // per-controller with @PreAuthorize (PB-002).
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(tenantResolvingFilter, JwtAuthenticationFilter.class)
                // Outermost: throttling happens before any password comparison.
                .addFilterBefore(loginRateLimitFilter, TenantResolvingFilter.class);

        return http.build();
    }

    /**
     * bcrypt with cost 12: the platform-wide password hashing scheme. The cost
     * is pinned by a unit test so it cannot silently regress.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_COST);
    }

    /**
     * CORS for the SPA dev server. The frontend normally reaches the API via
     * the Vite proxy (same-origin), but direct calls from the dev origin are
     * also permitted. Credentials stay disabled: auth travels in the
     * Authorization header, never in cookies.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${talentpipe.security.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Tenant-Subdomain"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
