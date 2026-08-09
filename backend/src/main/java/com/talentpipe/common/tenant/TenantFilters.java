package com.talentpipe.common.tenant;

/**
 * Names shared between the Hibernate tenant filter definition (on the
 * tenant-scoped entities) and {@link TenantFilterAspect}, which enables it.
 * A single source for the strings so the annotation and the enabling code can
 * never drift apart.
 */
public final class TenantFilters {

    /** Hibernate filter appending {@code tenant_id = :tenantId} to every query. */
    public static final String TENANT_FILTER = "tenantFilter";

    /** The filter's single parameter: the current tenant id. */
    public static final String PARAM_TENANT_ID = "tenantId";

    private TenantFilters() {
        // constants only
    }
}
