package com.talentpipe.security;

import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the bearer token and populates the Spring {@code SecurityContext}.
 * Runs directly after {@link TenantResolvingFilter}.
 *
 * <p>Behavior:</p>
 * <ul>
 *   <li>No token → pass through anonymously; authorization rules decide
 *       whether the endpoint requires authentication (401 via the entry point).</li>
 *   <li>Valid ACCESS token → authenticated principal with ROLE_&lt;role&gt;
 *       authority; the tenant claim set by the previous filter is thereby
 *       verified (same token, now signature-checked).</li>
 *   <li>Invalid/expired/tampered token → 401 with the uniform error envelope,
 *       {@link TenantContext} cleared immediately, chain STOPPED.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, SecurityErrorWriter errorWriter) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = BearerTokens.resolve(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserPrincipal principal = jwtTokenProvider.parseAccessToken(token);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException ex) {
            // The unverified tenant claim set by TenantResolvingFilter must not
            // outlive a failed validation — clear it before responding.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            return; // stop the chain
        }

        filterChain.doFilter(request, response);
    }
}
