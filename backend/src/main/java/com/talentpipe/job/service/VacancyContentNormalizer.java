package com.talentpipe.job.service;

import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.common.util.OptionKey;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.WeekDay;
import com.talentpipe.tenant.dto.ProfileTaxonomy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns a validated {@link JobVacancyRequest} into the {@link VacancyContent}
 * that is actually stored. Runs after bean validation and before any business
 * rule, so every rule downstream judges the values as they will be saved.
 *
 * <p>What it does to each kind of field:</p>
 * <ul>
 *   <li><strong>All free text</strong> — HTML is stripped (OWASP, via
 *       {@link HtmlSanitizer}). The advert is rendered to anonymous visitors
 *       on the public job board, so a stored {@code <script>} here would be
 *       stored XSS against every candidate who opens it.</li>
 *   <li><strong>Single-line text</strong> — trimmed, inner whitespace runs
 *       collapsed to one space.</li>
 *   <li><strong>Long text</strong> (summary, description, other requirements)
 *       — trimmed only; line breaks are the author's formatting.</li>
 *   <li><strong>Lists</strong> — each entry treated as single-line text, blanks
 *       dropped, duplicates removed case-insensitively (first spelling wins).</li>
 *   <li><strong>Benefits</strong> — matched against the company-profile
 *       benefits catalogue and rewritten to its canonical identifiers.</li>
 *   <li><strong>Working days</strong> — de-duplicated and put in week order.</li>
 * </ul>
 *
 * <p>These mirror {@code normalizeVacancy} in the frontend's
 * {@code jobVacancy.ts}; the server applies them regardless, because it is the
 * one that has to be right.</p>
 *
 * <p>Blank handling follows the columns: the text "basics" a draft may leave
 * empty are stored as {@code ""}, every other optional text as {@code null}.</p>
 */
@Component
public class VacancyContentNormalizer {

    private final HtmlSanitizer htmlSanitizer;

    public VacancyContentNormalizer(HtmlSanitizer htmlSanitizer) {
        this.htmlSanitizer = htmlSanitizer;
    }

    /**
     * @throws InvalidRequestException (400) if {@code benefits} names something
     *         that is not in the benefits catalogue
     */
    public VacancyContent normalize(JobVacancyRequest request) {
        return new VacancyContent(
                line(request.title()),
                line(request.department()),
                // Bean validation guarantees a value on every HTTP path; the
                // fallback only spares a non-HTTP caller a NullPointerException.
                request.openings() == null ? 1 : request.openings(),
                request.employmentType(),
                request.workplaceType(),
                line(request.location()),
                request.applicationDeadline(),

                text(request.jobSummary()),
                text(request.jobDescription()),
                list(request.keyResponsibilities()),

                list(request.requiredSkills()),
                list(request.preferredSkills()),
                request.minimumExperienceYears(),
                blankToNull(line(request.education())),
                list(request.certifications()),
                list(request.languageRequirements()),
                blankToNull(text(request.otherRequirements())),

                request.salaryMin(),
                request.salaryMax(),
                blankToNull(line(request.currency())),
                request.payPeriod(),
                benefits(request.benefits()),

                workingDays(request.workingDays()),
                blankToNull(line(request.workingHours())),
                request.shiftType(),
                request.expectedHoursPerWeek(),

                request.assignedRecruiterId(),
                request.hiringManagerId(),
                pipelineId(request.recruitmentPipelineId()),
                list(request.screeningQuestions()));
    }

    // ---------------------------------------------------------------- strings

    /** Single-line text: markup stripped, trimmed, inner whitespace collapsed. Never null. */
    private String line(String value) {
        return text(value).replaceAll("\\s+", " ");
    }

    /** Long text: markup stripped, outer whitespace trimmed, line breaks kept. Never null. */
    private String text(String value) {
        return value == null ? "" : htmlSanitizer.sanitize(value).trim();
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    /** Pipeline ids are identifiers, not prose: compared and stored upper-case. */
    private String pipelineId(String value) {
        String id = blankToNull(line(value));
        return id == null ? null : id.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ lists

    /** Cleans a free-text list: each entry as a line, blanks dropped, case-insensitive de-duplication. */
    private List<String> list(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        List<String> cleaned = new ArrayList<>(values.size());
        for (String raw : values) {
            String entry = line(raw);
            if (!entry.isEmpty() && seen.add(entry.toLowerCase(Locale.ROOT))) {
                cleaned.add(entry);
            }
        }
        return cleaned;
    }

    /**
     * Resolves each benefit to its canonical catalogue identifier.
     *
     * <p>The catalogue is the tenant module's {@link ProfileTaxonomy#BENEFITS}
     * — the same fixed set the company profile uses — and matching goes
     * through {@link OptionKey}, so {@code "health_insurance"} and
     * {@code "HEALTH-INSURANCE"} are the one option and are stored identically.</p>
     */
    private List<String> benefits(List<String> values) {
        List<String> canonical = new ArrayList<>();
        for (String entry : list(values)) {
            String key = OptionKey.of(entry);
            String match = ProfileTaxonomy.BENEFITS.stream()
                    .filter(option -> OptionKey.of(option).equals(key))
                    .findFirst()
                    .orElseThrow(() -> new InvalidRequestException(
                            "Choose benefits from the benefits catalogue only."));
            if (!canonical.contains(match)) {
                canonical.add(match);
            }
        }
        return canonical;
    }

    private static List<WeekDay> workingDays(List<WeekDay> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
