package com.talentpipe.testsupport;

import com.talentpipe.security.MethodSecurityConfig;
import com.talentpipe.security.Permission;
import com.talentpipe.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Security setup for {@code @WebMvcTest} controller slices, shareable across
 * modules.
 *
 * <p>The production {@code SecurityConfig} cannot load in a web slice — it
 * needs the JWT provider and friends, which a slice does not create. This
 * stands in with the same observable rules: method security is active
 * (including the {@code @RequirePermission} templates from
 * {@link MethodSecurityConfig}), {@code /api/v1/public/**} is open, everything
 * else needs authentication, an anonymous request answers 401 and a missing
 * permission answers 403.</p>
 */
@TestConfiguration
@EnableMethodSecurity
@Import(MethodSecurityConfig.class)
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
     * Authenticates a MockMvc request as a user of {@code tenantId} holding
     * one of the old fixed roles, with the authorities production would give
     * them (see {@link TestAuthorities#forRole}). The principal is a real
     * {@link UserPrincipal}, so {@code @AuthenticationPrincipal} sees what it
     * would in production.
     *
     * @param role a role name without the {@code ROLE_} prefix, e.g. {@code HR_MANAGER}
     */
    public static RequestPostProcessor asUser(UUID userId, UUID tenantId, String role) {
        return authenticated(new UserPrincipal(userId, tenantId, role, "user@example.com"),
                TestAuthorities.forRole(role));
    }

    /** Authenticates as a member of a custom role holding exactly {@code permissions}. */
    public static RequestPostProcessor asMemberWith(UUID userId, UUID tenantId, Permission... permissions) {
        return authenticated(new UserPrincipal(userId, tenantId, "CUSTOM", "user@example.com"),
                TestAuthorities.withPermissions(permissions));
    }

    private static RequestPostProcessor authenticated(UserPrincipal principal, List<GrantedAuthority> authorities) {
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
