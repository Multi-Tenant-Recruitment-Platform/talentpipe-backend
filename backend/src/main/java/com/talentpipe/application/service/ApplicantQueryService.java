package com.talentpipe.application.service;

import com.talentpipe.application.dto.ApplicantCounts;
import com.talentpipe.application.dto.ApplicantPage;
import com.talentpipe.application.dto.ApplicantRow;
import com.talentpipe.application.dto.ApplicantSummary;
import com.talentpipe.application.entity.ReviewStatus;
import com.talentpipe.application.entity.ScreeningBands;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.service.VacancyAccess;
import com.talentpipe.job.service.VacancyViewer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a vacancy's applicants for the recruiter's dashboard.
 *
 * <p>The vacancy is loaded through {@link VacancyAccess} first, so a vacancy
 * the caller may not see — another company's, or one they do not work on
 * without {@code applications.view.all} — answers 404 like a missing one.
 * Every query then names the tenant again; nothing here trusts the id alone.</p>
 *
 * <p>Plain SQL rather than JPQL: the ranking needs a CASE over review status
 * and NULLS LAST, the blind-mode label a window function, and the whole page
 * is one query with its joins — no lookup per row.</p>
 *
 * <p>Default order: advanced (by screening or by a recruiter), then manual
 * review, then not yet screened, then rejected, then withdrawn; within each,
 * highest score first, unscored last; then earliest application, then id, so
 * paging never shuffles.</p>
 */
@Service
public class ApplicantQueryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_QUERY_LENGTH = 100;

    private static final String GROUP_ORDER = """
            CASE r.review_status
                WHEN 'AUTO_ADVANCED' THEN 0
                WHEN 'ADVANCED_BY_RECRUITER' THEN 0
                WHEN 'MANUAL_REVIEW' THEN 1
                WHEN 'PENDING_SCREENING' THEN 2
                WHEN 'REJECTED' THEN 3
                ELSE 4
            END""";

    /** Every application of the vacancy, numbered in the order they arrived (the blind-mode label). */
    private static final String RANKED = """
            WITH ranked AS (
                SELECT a.id, a.candidate_id, a.created_at, a.review_status, a.current_stage_id,
                       row_number() OVER (ORDER BY a.created_at, a.id) AS seq
                FROM applications a
                WHERE a.tenant_id = :tenantId AND a.job_id = :jobId
            )
            """;

    private static final String FROM = """
            FROM ranked r
            JOIN candidates c ON c.id = r.candidate_id
            LEFT JOIN application_scores s ON s.application_id = r.id AND s.tenant_id = :tenantId
            LEFT JOIN job_stages st ON st.id = r.current_stage_id AND st.tenant_id = :tenantId
            """;

    private final VacancyAccess vacancyAccess;
    private final NamedParameterJdbcTemplate jdbc;

    public ApplicantQueryService(VacancyAccess vacancyAccess, NamedParameterJdbcTemplate jdbc) {
        this.vacancyAccess = vacancyAccess;
        this.jdbc = jdbc;
    }

    /** Which applicants to list. */
    public enum Band {
        ALL, AUTO_ADVANCED, MANUAL_REVIEW, PENDING, REJECTED, WITHDRAWN;

        static Band parse(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "all" -> ALL;
                case "auto", "auto_advanced", "advanced" -> AUTO_ADVANCED;
                case "manual", "manual_review" -> MANUAL_REVIEW;
                case "pending" -> PENDING;
                case "rejected" -> REJECTED;
                case "withdrawn" -> WITHDRAWN;
                default -> throw new InvalidRequestException(
                        "band must be one of all, auto, manual, pending, rejected, withdrawn");
            };
        }

        String condition() {
            return switch (this) {
                case ALL -> "TRUE";
                case AUTO_ADVANCED -> "r.review_status IN ('AUTO_ADVANCED', 'ADVANCED_BY_RECRUITER')";
                case MANUAL_REVIEW -> "r.review_status = 'MANUAL_REVIEW'";
                case PENDING -> "r.review_status = 'PENDING_SCREENING'";
                case REJECTED -> "r.review_status = 'REJECTED'";
                case WITHDRAWN -> "r.review_status = 'WITHDRAWN'";
            };
        }
    }

    /** How to order the list. */
    public enum SortBy {
        DEFAULT, SCORE, APPLIED_AT, NAME;

        static SortBy parse(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "default" -> DEFAULT;
                case "score" -> SCORE;
                case "appliedat", "applied_at", "applied" -> APPLIED_AT;
                case "name" -> NAME;
                default -> throw new InvalidRequestException("sort must be one of default, score, appliedAt, name");
            };
        }
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("java:S107") // one parameter per query-string option
    public ApplicantPage list(VacancyViewer viewer, UUID jobId, String band, String q, String sort,
                              String direction, int page, int size) {
        JobVacancy vacancy = vacancyAccess.require(viewer, jobId);
        Band filter = Band.parse(band);
        SortBy sortBy = SortBy.parse(sort);
        boolean ascending = parseDirection(direction, sortBy);
        boolean blind = vacancy.isBlindModeEnabled();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", viewer.tenantId())
                .addValue("jobId", jobId);
        StringBuilder where = new StringBuilder(" WHERE ").append(filter.condition());
        // Searching by name or email would reveal who is behind a blind label.
        String search = blind ? null : likePattern(q);
        if (search != null) {
            where.append(" AND (lower(c.full_name) LIKE :q ESCAPE '\\' OR lower(c.email) LIKE :q ESCAPE '\\')");
            params.addValue("q", search);
        }

        Long total = jdbc.queryForObject(RANKED + "SELECT count(*) " + FROM + where, params, Long.class);
        long totalElements = total == null ? 0 : total;

        params.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        List<ApplicantRow> rows = jdbc.query(RANKED + """
                SELECT r.id, r.seq, r.created_at, r.review_status,
                       c.full_name, c.email,
                       s.status AS score_status, s.overall_score,
                       st.id AS stage_id, st.name AS stage_name, st.stage_type
                """ + FROM + where + " ORDER BY " + orderBy(sortBy, ascending, blind)
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> row(rs, blind));

        ScreeningBands bands = ScreeningBands.of(vacancy);
        int totalPages = (int) ((totalElements + safeSize - 1) / safeSize);
        return new ApplicantPage(rows, safePage, safeSize, totalElements, totalPages,
                counts(viewer.tenantId(), jobId),
                new ApplicantPage.Thresholds(bands.auto(), bands.manual(), bands.fromPipeline()),
                blind, lastChangedAt(viewer.tenantId(), jobId));
    }

    @Transactional(readOnly = true)
    public ApplicantSummary summary(VacancyViewer viewer, UUID jobId) {
        vacancyAccess.require(viewer, jobId);
        return new ApplicantSummary(counts(viewer.tenantId(), jobId), lastChangedAt(viewer.tenantId(), jobId));
    }

    // ----------------------------------------------------------------- parts

    private ApplicantCounts counts(UUID tenantId, UUID jobId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT count(*) AS all_count,
                       count(*) FILTER (WHERE review_status IN ('AUTO_ADVANCED', 'ADVANCED_BY_RECRUITER')) AS advanced,
                       count(*) FILTER (WHERE review_status = 'ADVANCED_BY_RECRUITER') AS by_recruiter,
                       count(*) FILTER (WHERE review_status = 'MANUAL_REVIEW') AS manual,
                       count(*) FILTER (WHERE review_status = 'PENDING_SCREENING') AS pending,
                       count(*) FILTER (WHERE review_status = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE review_status = 'WITHDRAWN') AS withdrawn
                FROM applications
                WHERE tenant_id = :tenantId AND job_id = :jobId
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("jobId", jobId));
        return new ApplicantCounts(longOf(row, "all_count"), longOf(row, "advanced"), longOf(row, "by_recruiter"),
                longOf(row, "manual"), longOf(row, "pending"), longOf(row, "rejected"), longOf(row, "withdrawn"));
    }

    /** The latest change to any of the vacancy's applications, their scores or their histories. */
    private Instant lastChangedAt(UUID tenantId, UUID jobId) {
        Timestamp at = jdbc.queryForObject("""
                SELECT greatest(
                    (SELECT max(a.updated_at) FROM applications a
                     WHERE a.tenant_id = :tenantId AND a.job_id = :jobId),
                    (SELECT max(s.updated_at) FROM application_scores s
                     JOIN applications a ON a.id = s.application_id
                     WHERE a.tenant_id = :tenantId AND a.job_id = :jobId),
                    (SELECT max(e.created_at) FROM application_stage_events e
                     JOIN applications a ON a.id = e.application_id
                     WHERE a.tenant_id = :tenantId AND a.job_id = :jobId))
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("jobId", jobId),
                Timestamp.class);
        return at == null ? null : at.toInstant();
    }

    private static String orderBy(SortBy sortBy, boolean ascending, boolean blind) {
        String dir = ascending ? "ASC" : "DESC";
        return switch (sortBy) {
            case DEFAULT -> GROUP_ORDER + ", s.overall_score DESC NULLS LAST, r.created_at ASC, r.id ASC";
            case SCORE -> "s.overall_score " + dir + " NULLS LAST, r.created_at ASC, r.id ASC";
            case APPLIED_AT -> "r.created_at " + dir + ", r.id " + dir;
            // In blind mode the shown name is the arrival number, so that is what "name" sorts by.
            case NAME -> (blind ? "r.seq " : "lower(c.full_name) ") + dir + ", r.id ASC";
        };
    }

    /** Ascending unless asked otherwise — except score and applied date, newest and highest first. */
    private static boolean parseDirection(String direction, SortBy sortBy) {
        if (direction == null || direction.isBlank()) {
            return sortBy == SortBy.NAME || sortBy == SortBy.DEFAULT;
        }
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new InvalidRequestException("direction must be asc or desc");
        };
    }

    /** {@code %term%} with LIKE's own wildcards escaped, or null when there is nothing to search for. */
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String term = q.trim().toLowerCase(Locale.ROOT);
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException("q must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static ApplicantRow row(ResultSet rs, boolean blind) throws SQLException {
        UUID stageId = rs.getObject("stage_id", UUID.class);
        ApplicantRow.StageRef stage = stageId == null ? null
                : new ApplicantRow.StageRef(stageId, rs.getString("stage_name"), rs.getString("stage_type"));
        Integer score = (Integer) rs.getObject("overall_score");
        String scoreStatus = rs.getString("score_status");
        return new ApplicantRow(
                rs.getObject("id", UUID.class),
                blind ? "Applicant #" + rs.getLong("seq") : rs.getString("full_name"),
                blind ? null : rs.getString("email"),
                rs.getTimestamp("created_at").toInstant(),
                score,
                scoreStatus == null ? "PENDING" : scoreStatus,
                ReviewStatus.valueOf(rs.getString("review_status")),
                stage);
    }

    private static long longOf(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : ((Number) value).longValue();
    }
}
