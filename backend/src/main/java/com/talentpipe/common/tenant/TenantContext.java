package com.talentpipe.common.tenant;

import java.util.UUID;

/**
 * Per-request holder of the current tenant id, resolved from the JWT by
 * {@code TenantResolvingFilter}.
 *
 * <p><strong>Lifecycle contract (security-critical):</strong> servlet worker
 * threads are pooled and reused across requests. Whoever sets a value here is
 * responsible for clearing it in a {@code finally} block before the request
 * thread is returned to the pool — a stale tenant id on a reused thread would
 * be a cross-tenant data leak. This contract is guarded by a dedicated
 * integration test (see {@code TenantContextLeakIntegrationTest}).</p>
 *
 * <p>Tenant identity must ONLY ever be derived from verified request context
 * (JWT claim / resolved subdomain) — never from a request body or path
 * parameter.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // static holder — not instantiable
    }

    /** Binds the given tenant id to the current thread. */
    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * @return the tenant id bound to the current thread, or {@code null} when
     *         no tenant is in scope (unauthenticated request or SUPER_ADMIN).
     */
    public static UUID get() {
        return CURRENT_TENANT.get();
    }

    /** Unbinds the tenant id from the current thread. MUST run in a finally block. */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
