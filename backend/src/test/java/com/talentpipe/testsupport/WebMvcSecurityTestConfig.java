package com.talentpipe.testsupport;

import com.talentpipe.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Security setup for {@code @WebMvcTest} controller slices, shareable across
 * modules.
 *
 * <p>The production {@code SecurityConfig} cannot load in a web slice — it
 * needs the JWT provider and friends, which a slice does not create. This
 * stands in with the same observable rules: {@code @PreAuthorize} is active,
 * {@code /api/v1/public/**} is open, everything else needs authentication, an
 * anonymous request answers 401 and a wrong role answers 403.</p>
 *
 * <p>It mirrors the package-private {@code TestSecurityConfig} the tenant
 * controller tests use; that one could be replaced by this, which was left for
 * its owners to do rather than changed from another module's branch.</p>
 */
@TestConfiguration
@EnableMethodSecurity
public class WebMvcSecurityTestConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"))
                        .accessDeniedHandler((request, response, ex) ->
                                response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden")));
        return http.build();
    }

    /**
     * Authenticates a MockMvc request as a user of {@code tenantId} with the
     * given role, placing a real {@link UserPrincipal} in the security context
     * — exactly what {@code JwtAuthenticationFilter} does at runtime, so
     * {@code @AuthenticationPrincipal} and {@code @PreAuthorize} see what they
     * would in production.
     *
     * @param role a role name without the {@code ROLE_} prefix, e.g. {@code HR_MANAGER}
     */
    public static RequestPostProcessor asUser(UUID userId, UUID tenantId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, tenantId, role, "user@example.com");
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
