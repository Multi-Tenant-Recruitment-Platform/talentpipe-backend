package com.talentpipe.security;

import com.talentpipe.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First security filter in the chain: extracts the (still unverified)
 * {@code tenant_id} claim from the bearer token and binds it to
 * {@link TenantContext} for the duration of the request. Full token
 * validation happens immediately afterwards in {@link JwtAuthenticationFilter},
 * which rejects the request (and clears the context) if the token is invalid —
 * so the provisional value never survives onto an authenticated request path.
 *
 * <p><strong>Only company endpoints get a tenant.</strong> Sign-in and its
 * emailed links ({@code /api/v1/auth/**}) and the public site
 * ({@code /api/v1/public/**}) work across companies by design: login finds the
 * account in whichever company it names, a reset or invitation link belongs to
 * whoever it was sent to, and the job board lists every company. The browser
 * still sends whatever token it holds to them, so binding that token's tenant
 * would let the ORM tenant filter ({@code TenantFilterAspect}) hide the very
 * account or vacancy the request is about. Those paths are left without a
 * tenant, exactly as an anonymous request is.</p>
 *
 * <p><strong>CRITICAL:</strong> the {@code finally} block below is the
 * platform's tenant-isolation backstop. Servlet worker threads are pooled and
 * reused across requests; a tenant id left on the thread would leak into the
 * next request served by that thread — a cross-tenant data breach. Guarded by
 * {@code TenantContextLeakIntegrationTest}. Do not remove or reorder.</p>
 */
public class TenantResolvingFilter extends OncePerRequestFilter {

    /** Endpoints that work across companies by design; see the class comment. */
    private static final List<String> CROSS_TENANT_PREFIXES = List.of("/api/v1/auth/", "/api/v1/public/");

    private final JwtTokenProvider jwtTokenProvider;

    public TenantResolvingFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = BearerTokens.resolve(request);
            if (token != null && isCompanyScoped(request)) {
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

    /** False for sign-in, its emailed links and the public site, which span companies. */
    static boolean isCompanyScoped(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return CROSS_TENANT_PREFIXES.stream().noneMatch(path::startsWith);
    }
}
