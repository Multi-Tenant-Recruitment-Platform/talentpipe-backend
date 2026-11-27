package com.talentpipe.application.controller;

import com.talentpipe.job.service.VacancyViewer;
import com.talentpipe.security.Authorities;
import com.talentpipe.security.Permission;
import com.talentpipe.security.UserPrincipal;
import org.springframework.security.core.Authentication;

/** Who is looking at applicants, and how far they see. */
final class ApplicantViewers {

    private ApplicantViewers() {
        // static helper only
    }

    /** Every vacancy's applicants with {@code applications.view.all}; otherwise those of vacancies worked on. */
    static VacancyViewer of(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return new VacancyViewer(principal.tenantId(), principal.id(),
                Authorities.has(authentication, Permission.APPLICATIONS_VIEW_ALL));
    }
}
