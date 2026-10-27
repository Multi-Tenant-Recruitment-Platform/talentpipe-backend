package com.talentpipe.common.tenant;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * ORM-level tenant isolation: whenever a repository is invoked while a tenant
 * is bound to {@link TenantContext}, this aspect enables the Hibernate
 * {@code tenantFilter} on the transaction's session, so every query against a
 * tenant-scoped entity carries {@code AND tenant_id = :currentTenant} at the
 * SQL level — including loads by primary key ({@code applyToLoadByKey} on the
 * filter definition).
 *
 * <p><strong>Why this exists:</strong> before it, isolation depended on every
 * service author remembering to write the {@code tenant_id} predicate by hand.
 * One forgotten {@code findById} would silently serve another company's data.
 * With the filter, a cross-tenant row is invisible to the query itself: the
 * lookup comes back empty and the platform's 404-over-403 convention takes
 * over — existence never leaks across tenants. Manual scoping in services
 * remains as a second, independent layer.</p>
 *
 * <p><strong>When the filter is NOT enabled (by design):</strong></p>
 * <ul>
 *   <li>No tenant in context — unauthenticated flows (login, register, token
 *       links), candidate requests and SUPER_ADMIN requests, whose tokens carry
 *       no tenant claim. These legitimately query across tenants (e.g. global
 *       login's find-by-email) or touch un-scoped tables.</li>
 *   <li>No transaction-bound session yet — every read/write path in this
 *       codebase enters through a {@code @Transactional} service method, so by
 *       the time a repository runs, the session exists and gets the filter.</li>
 *   <li>Async notification threads — {@code TenantContext} is a ThreadLocal, so
 *       the email dispatcher's pool threads have no tenant and their writes to
 *       the notifications table are unfiltered, which is correct: the row's
 *       tenant comes from the event, not from ambient context.</li>
 * </ul>
 *
 * <p>The filter is left enabled for the remainder of the session: sessions are
 * transaction-scoped and die at commit, and {@code TenantContext} is cleared
 * per request, so no state can leak to the next request even on a reused
 * pooled thread.</p>
 */
@Aspect
@Component
public class TenantFilterAspect {

    private final EntityManagerFactory entityManagerFactory;

    public TenantFilterAspect(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    /** Matches every Spring Data repository method in any module. */
    @Around("execution(* com.talentpipe..repository..*(..))")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = TenantContext.get();
        if (tenantId != null) {
            // Only a session already bound to the current transaction can be
            // filtered; unwrapping outside one would create a throwaway session
            // the repository never uses.
            EntityManagerHolder holder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(entityManagerFactory);
            if (holder != null) {
                Session session = holder.getEntityManager().unwrap(Session.class);
                if (session.getEnabledFilter(TenantFilters.TENANT_FILTER) == null) {
                    session.enableFilter(TenantFilters.TENANT_FILTER)
                            .setParameter(TenantFilters.PARAM_TENANT_ID, tenantId);
                }
            }
        }
        return joinPoint.proceed();
    }
}
