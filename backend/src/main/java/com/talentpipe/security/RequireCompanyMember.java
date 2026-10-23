package com.talentpipe.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Allows the annotated endpoint to any active company user, whatever their
 * role; candidates and platform operators get 403.
 *
 * <p>For things everyone in a workspace may do — read the company profile,
 * list the vacancies they can see. What each person then sees is narrowed by
 * the service, not here.</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('" + Authorities.COMPANY_MEMBER + "')")
public @interface RequireCompanyMember {
}
