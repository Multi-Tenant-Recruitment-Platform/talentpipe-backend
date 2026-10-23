package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.VacancyTeamMember;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * The dashboard list's filters as composable query parts. Every list starts
 * from {@link #inTenant}; the rest are added only when they apply, so the
 * statement holds just the predicates that narrow it.
 */
public final class VacancySpecifications {

    private static final String TENANT_ID = "tenantId";
    private static final String RECRUITER_ID = "assignedRecruiterId";
    private static final String MANAGER_ID = "hiringManagerId";

    private VacancySpecifications() {
        // static factories only
    }

    /** The tenant's vacancies — the base of every recruiter-side list. */
    public static Specification<JobVacancy> inTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get(TENANT_ID), tenantId);
    }

    public static Specification<JobVacancy> statusIn(Collection<VacancyStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /** Department matched whole, ignoring case — as the board's category filter does. */
    public static Specification<JobVacancy> inDepartment(String department) {
        String wanted = department.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("department")), wanted);
    }

    /**
     * Vacancies the user works on: created, recruits for, manages, or is on
     * the hiring team of. Same rule as {@link JobVacancyRepository#WORKS_ON}.
     */
    public static Specification<JobVacancy> worksOn(UUID userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("createdBy"), userId),
                assignment(root, query, cb, userId));
    }

    /**
     * Vacancies the user has been put on: as recruiter, hiring manager or
     * hiring team member. Unlike {@link #worksOn}, having created a vacancy
     * does not count — "assigned to me" is about the work handed to you.
     */
    public static Specification<JobVacancy> assignedTo(UUID userId) {
        return (root, query, cb) -> assignment(root, query, cb, userId);
    }

    private static Predicate assignment(Root<JobVacancy> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                        UUID userId) {
        Subquery<Integer> onTeam = query.subquery(Integer.class);
        Root<VacancyTeamMember> member = onTeam.from(VacancyTeamMember.class);
        onTeam.select(cb.literal(1)).where(
                cb.equal(member.get("vacancyId"), root.get("id")),
                cb.equal(member.get("userId"), userId));
        return cb.or(
                cb.equal(root.get(RECRUITER_ID), userId),
                cb.equal(root.get(MANAGER_ID), userId),
                cb.exists(onTeam));
    }
}
