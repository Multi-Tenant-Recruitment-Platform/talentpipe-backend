package com.talentpipe.application.service;

import com.talentpipe.application.dto.ApplicantCounts;
import com.talentpipe.application.dto.ApplicantVacancy;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.service.VacancyViewer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vacancies whose applicants the caller may review, with their counts —
 * the first screen of the applicants area.
 *
 * <p>The same scope as the applicant list itself: everyone with
 * {@code applications.view.all} sees the whole company's vacancies, everyone
 * else only those they work on — the rule {@code JobVacancyRepository.WORKS_ON}
 * states, repeated here in SQL because the counts are aggregated in the same
 * query. Archived vacancies are never listed.</p>
 */
@Service
public class ApplicantVacancyService {

    /** Who works on a vacancy: the same five ways as {@code JobVacancyRepository.WORKS_ON}. */
    private static final String WORKS_ON = """
            (v.created_by = :userId
             OR v.assigned_recruiter_id = :userId
             OR v.hiring_manager_id = :userId
             OR EXISTS (SELECT 1 FROM job_vacancy_team_members m
                        WHERE m.vacancy_id = v.id AND m.user_id = :userId)
             OR EXISTS (SELECT 1 FROM job_stage_interviewers i
                        WHERE i.job_id = v.id AND i.user_id = :userId))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ApplicantVacancyService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Which vacancies to list. */
    public enum Status {
        PUBLISHED, CLOSED;

        static Status parse(String value) {
            if (value == null || value.isBlank()) {
                return PUBLISHED;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "published" -> PUBLISHED;
                case "closed" -> CLOSED;
                default -> throw new InvalidRequestException("status must be published or closed");
            };
        }
    }

    /** Newest application first; vacancies nobody has applied to yet, most recently published first. */
    @Transactional(readOnly = true)
    public List<ApplicantVacancy> list(VacancyViewer viewer, String status) {
        Status wanted = Status.parse(status);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", viewer.tenantId())
                .addValue("userId", viewer.userId())
                .addValue("status", wanted.name());
        String scope = viewer.seesAllVacancies() ? "" : " AND " + WORKS_ON;
        return jdbc.query("""
                SELECT v.id, v.title, v.department, v.location, v.employment_type, v.status,
                       v.published_at, v.application_deadline, v.blind_mode_enabled,
                       count(a.id) AS all_count,
                       count(a.id) FILTER (WHERE a.review_status IN ('AUTO_ADVANCED', 'ADVANCED_BY_RECRUITER')) AS advanced,
                       count(a.id) FILTER (WHERE a.review_status = 'ADVANCED_BY_RECRUITER') AS by_recruiter,
                       count(a.id) FILTER (WHERE a.review_status = 'MANUAL_REVIEW') AS manual,
                       count(a.id) FILTER (WHERE a.review_status = 'PENDING_SCREENING') AS pending,
                       count(a.id) FILTER (WHERE a.review_status = 'REJECTED') AS rejected,
                       count(a.id) FILTER (WHERE a.review_status = 'WITHDRAWN') AS withdrawn,
                       max(a.created_at) AS last_applied_at
                FROM job_vacancies v
                LEFT JOIN applications a ON a.job_id = v.id AND a.tenant_id = v.tenant_id
                WHERE v.tenant_id = :tenantId AND v.status = :status
                """ + scope + """
                 GROUP BY v.id
                ORDER BY max(a.created_at) DESC NULLS LAST, v.published_at DESC NULLS LAST, v.id
                """, params, (rs, i) -> row(rs));
    }

    private static ApplicantVacancy row(ResultSet rs) throws SQLException {
        Timestamp published = rs.getTimestamp("published_at");
        Timestamp lastApplied = rs.getTimestamp("last_applied_at");
        Date deadline = rs.getDate("application_deadline");
        return new ApplicantVacancy(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("department"),
                rs.getString("location"),
                rs.getString("employment_type"),
                rs.getString("status"),
                published == null ? null : published.toInstant(),
                deadline == null ? null : deadline.toLocalDate(),
                rs.getBoolean("blind_mode_enabled"),
                new ApplicantCounts(rs.getLong("all_count"), rs.getLong("advanced"), rs.getLong("by_recruiter"),
                        rs.getLong("manual"), rs.getLong("pending"), rs.getLong("rejected"), rs.getLong("withdrawn")),
                lastApplied == null ? null : lastApplied.toInstant());
    }
}
