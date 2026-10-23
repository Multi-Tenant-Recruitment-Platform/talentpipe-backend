package com.talentpipe.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Allows the annotated endpoint only to callers whose role holds the given
 * permission; everyone else gets 403.
 *
 * <pre>{@code @RequirePermission(Permission.Keys.JOBS_PUBLISH)}</pre>
 *
 * <p>A meta-annotation over {@code @PreAuthorize}, expanded by the template
 * defaults registered in {@link MethodSecurityConfig}. Use the
 * {@link Permission.Keys} constants rather than string literals; a build test
 * also fails on any value that is not in the catalogue.</p>
 *
 * <p>This is the role half of the platform's double check. Services still
 * scope every lookup to the caller's tenant, so a permission never reaches
 * another company's data.</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('{value}')")
public @interface RequirePermission {

    /** A {@link Permission#key()}. */
    String value();
}
