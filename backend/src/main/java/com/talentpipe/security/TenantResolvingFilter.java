package com.talentpipe.security;

import com.talentpipe.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First security filter in the chain: extracts the (still unverified)
 * {@code tenant_id} claim from the bearer token and binds it to
 * {@link TenantContext} for the duration of the request. Full token
 * validation happens immediately afterwards in {@link JwtAuthenticationFilter},
 * which rejects the request (and clears the context) if the token is invalid —
 * so the provisional value never survives onto an authenticated request path.
 *
 * <p><strong>CRITICAL:</strong> the {@code finally} block below is the
 * platform's tenant-isolation backstop. Servlet worker threads are pooled and
 * reused across requests; a tenant id left on the thread would leak into the
 * next request served by that thread — a cross-tenant data breach. Guarded by
 * {@code TenantContextLeakIntegrationTest}. Do not remove or reorder.</p>
 */
public class TenantResolvingFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public TenantResolvingFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = BearerTokens.resolve(request);
            if (token != null) {
                jwtTokenProvider.peekTenantId(token).ifPresent(TenantContext::set);
            }
            filterChain.doFilter(request, response);
        } finally {
            // Always executed — even on exceptions anywhere downstream — so a
            // pooled worker thread can never carry a stale tenant id into the
            // next request it serves.
            TenantContext.clear();
        }
    }
}
