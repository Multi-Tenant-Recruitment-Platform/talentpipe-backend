package com.talentpipe.job.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.dto.IdealMatchingRequest;
import com.talentpipe.job.dto.IdealMatchingResponse;
import com.talentpipe.job.dto.MatchingCriterionRequest;
import com.talentpipe.job.dto.ScreeningProfileResponse;
import com.talentpipe.job.entity.JobScreeningCriteria;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.MatchingCategory;
import com.talentpipe.job.entity.MatchingRequirement;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.repository.JobScreeningCriteriaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A vacancy's ideal candidate (V27): what the person creating it says the best
 * applicant looks like, and how much each part counts.
 *
 * <p>The weighted criteria — each technical skill, each tool and relevant
 * experience — share one 100%. Skills and tools also say whether they are a
 * must-have or preferred, which screening uses to call out a missing must-have;
 * the weight alone decides the score. Degree types and nice-to-have
 * certifications are never weighted: they travel to screening as context.</p>
 *
 * <p>A draft may be saved with the profile half done — any total, weights not
 * yet decided. Publishing needs exactly 100%, and a live vacancy must stay at
 * 100% when its profile changes. Matching is only available where the
 * vacancy's pipeline template offers it.</p>
 *
 * <p>Nothing here calls the AI service. {@link #screeningProfile} is the data
 * it will be sent (MRP-361).</p>
 */
@Service
public class IdealMatchingService {

    private static final Logger log = LoggerFactory.getLogger(IdealMatchingService.class);

    /** Said in front of what is missing when a vacancy is published. */
    public static final String NOT_READY_TO_PUBLISH = "This vacancy can't be published yet";

    /** Said in front of what is missing when a live vacancy's profile changes. */
    public static final String MUST_STAY_COMPLETE = "A published vacancy's ideal candidate weights must total 100%";

    /** What relevant experience is called when the request gives it no name. */
    static final String EXPERIENCE_NAME = "Relevant experience";

    static final String NEEDS_A_NAME = "Give every skill and tool in the ideal candidate a name.";
    static final String EXPERIENCE_ONCE = "Relevant experience can be weighed only once.";

    /**
     * The education levels that are a degree, from the job form's list; only
     * these take accepted degree types.
     */
    static final Set<String> DEGREE_LEVELS = Set.of("Diploma", "Bachelor’s degree", "Master’s degree", "Doctorate");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final VacancyAccess access;
    private final JobScreeningCriteriaRepository criteriaRepository;

    public IdealMatchingService(VacancyAccess access, JobScreeningCriteriaRepository criteriaRepository) {
        this.access = access;
        this.criteriaRepository = criteriaRepository;
    }

    // ------------------------------------------------------------------ reads

    /** @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see */
    @Transactional(readOnly = true)
    public IdealMatchingResponse get(VacancyViewer viewer, UUID vacancyId) {
        return describe(access.require(viewer, vacancyId));
    }

    /**
     * What AI screening needs to score this vacancy's applicants. With
     * matching off, no criteria are sent and screening uses its defaults.
     *
     * @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see
     */
    @Transactional(readOnly = true)
    public ScreeningProfileResponse screeningProfile(VacancyViewer viewer, UUID vacancyId) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        VacancyContent content = vacancy.content();
        boolean enabled = vacancy.isIdealMatchingEnabled();
        List<JobScreeningCriteria> criteria = enabled ? stored(vacancy) : List.of();

        List<ScreeningProfileResponse.CriterionWeight> weights = criteria.stream()
                .filter(criterion -> criterion.getWeight() != null)
                .map(criterion -> new ScreeningProfileResponse.CriterionWeight(
                        criterion.getName(), criterion.getWeight().doubleValue()))
                .toList();
        return new ScreeningProfileResponse(
                vacancy.getId(),
                enabled,
                vacancy.isBlindModeEnabled(),
                content.title(),
                content.jobSummary(),
                content.jobDescription(),
                weights,
                namesWith(criteria, MatchingRequirement.MUST_HAVE),
                namesWith(criteria, MatchingRequirement.PREFERRED),
                content.toolsAndTechnologies(),
                content.minimumExperienceYears(),
                content.education(),
                enabled ? vacancy.getMatchingDegreeTypes() : List.of(),
                enabled ? vacancy.getMatchingNiceToHaveCertifications() : List.of());
    }

    // ----------------------------------------------------------------- writes

    /**
     * Replaces the vacancy's ideal candidate.
     *
     * @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see
     * @throws BusinessRuleException (422) the vacancy is closed or archived;
     *         matching is switched on where the template does not offer it; a
     *         criterion breaks a rule ({@link #prepare}); or the vacancy is
     *         live and its weights would not total 100%
     */
    @Transactional
    public IdealMatchingResponse replace(VacancyViewer viewer, UUID vacancyId, IdealMatchingRequest request) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        List<Wanted> criteria = prepare(vacancy, request);
        store(vacancy, criteria);
        if (vacancy.getStatus() == VacancyStatus.PUBLISHED) {
            requireComplete(vacancy, MUST_STAY_COMPLETE);
        }
        return describe(vacancy);
    }

    /**
     * Checks a profile and puts its switch and unweighted lists on the vacancy,
     * before the vacancy is saved — so the version a create or an edit returns
     * already includes them. The criteria are stored afterwards ({@link #store}),
     * once the vacancy has an id.
     *
     * <p>Rules in every state: names are given (experience may leave its out)
     * and unique ignoring case across skills, tools and experience — the AI
     * service refuses two that differ only in case; skills and tools say
     * whether they are a must-have; relevant experience appears once. Degree
     * types are kept only when the education asked for is a degree.</p>
     *
     * @return the criteria to store, in order, repeats and blanks cleaned
     * @throws BusinessRuleException (422) as {@link #replace}, completeness aside
     */
    public List<Wanted> prepare(JobVacancy vacancy, IdealMatchingRequest request) {
        List<Wanted> criteria = criteriaOf(request);
        List<String> degreeTypes = isDegree(vacancy.content().education()) ? clean(request.degreeTypes()) : List.of();
        vacancy.describeIdealCandidate(request.enabled(), degreeTypes, clean(request.niceToHaveCertifications()));
        return criteria;
    }

    /** Replaces the vacancy's stored criteria with {@code criteria}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void store(JobVacancy vacancy, List<Wanted> criteria) {
        List<JobScreeningCriteria> before = stored(vacancy);
        criteriaRepository.deleteAll(before);
        // Flushed so a name kept from before does not collide with its own old row.
        criteriaRepository.flush();
        List<JobScreeningCriteria> rows = new ArrayList<>();
        for (int i = 0; i < criteria.size(); i++) {
            Wanted criterion = criteria.get(i);
            rows.add(new JobScreeningCriteria(vacancy.getTenantId(), vacancy.getId(), criterion.category(),
                    criterion.name(), fraction(criterion.weightPercent()), criterion.requirement(), i));
        }
        criteriaRepository.saveAllAndFlush(rows);
        log.info("Vacancy {} ideal candidate saved: matching {}, {} criteria [tenant={}]",
                vacancy.getId(), vacancy.isIdealMatchingEnabled() ? "on" : "off", rows.size(), vacancy.getTenantId());
    }

    /**
     * Gives a duplicate the source's ideal candidate. The switch and lists go
     * on {@code copy} before it is saved; call {@link #store} with the answer
     * afterwards. A copy whose pipeline no longer offers matching keeps the
     * criteria but has matching off.
     */
    public List<Wanted> prepareCopy(JobVacancy source, JobVacancy copy) {
        copy.describeIdealCandidate(source.isIdealMatchingEnabled() && copy.isIdealMatchingAvailable(),
                source.getMatchingDegreeTypes(), source.getMatchingNiceToHaveCertifications());
        return stored(source).stream()
                .map(row -> new Wanted(row.getCategory(), row.getName(), percent(row.getWeight()),
                        row.getRequirement()))
                .toList();
    }

    // ------------------------------------------------------------ completeness

    /**
     * Refuses a stored vacancy whose matching is on but whose weights do not
     * total 100%. Matching off passes.
     *
     * @throws BusinessRuleException (422) naming what is wrong
     */
    @Transactional(readOnly = true)
    public void requireComplete(JobVacancy vacancy, String lead) {
        if (!vacancy.isIdealMatchingEnabled()) {
            return;
        }
        List<Wanted> criteria = stored(vacancy).stream()
                .map(row -> new Wanted(row.getCategory(), row.getName(), percent(row.getWeight()),
                        row.getRequirement()))
                .toList();
        refuseIfIncomplete(criteria, lead);
    }

    /**
     * {@link #requireComplete} for a vacancy that is not stored yet: its
     * profile is the one the create request carries.
     */
    public static void requireComplete(IdealMatchingRequest request, String lead) {
        if (request == null || !request.enabled()) {
            return;
        }
        refuseIfIncomplete(criteriaOf(request), lead);
    }

    private static void refuseIfIncomplete(List<Wanted> criteria, String lead) {
        List<String> problems = new ArrayList<>();
        if (criteria.isEmpty()) {
            problems.add("weigh at least one skill, tool or relevant experience for the ideal candidate");
        }
        List<String> unweighted = criteria.stream()
                .filter(criterion -> criterion.weightPercent() == null)
                .map(Wanted::name)
                .toList();
        if (!unweighted.isEmpty()) {
            problems.add("give " + joinNames(unweighted) + " a weight or remove "
                    + (unweighted.size() == 1 ? "it" : "them"));
        }
        int total = total(criteria);
        if (!criteria.isEmpty() && total != 100) {
            problems.add("the ideal candidate weights add up to " + total + "%; they need to total 100%");
        }
        if (!problems.isEmpty()) {
            throw new BusinessRuleException(lead + ": " + String.join("; ", problems) + ".");
        }
    }

    // ---------------------------------------------------------------- private

    /** The request's criteria, checked and cleaned. */
    private static List<Wanted> criteriaOf(IdealMatchingRequest request) {
        List<Wanted> criteria = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean experience = false;
        for (MatchingCriterionRequest criterion : request.criteriaOrEmpty()) {
            MatchingCategory category = Objects.requireNonNull(criterion.category(), "category");
            String name = clean(criterion.name());
            if (category == MatchingCategory.EXPERIENCE) {
                if (experience) {
                    throw new BusinessRuleException(EXPERIENCE_ONCE);
                }
                experience = true;
                name = name.isEmpty() ? EXPERIENCE_NAME : name;
            } else {
                if (name.isEmpty()) {
                    throw new BusinessRuleException(NEEDS_A_NAME);
                }
                if (criterion.requirement() == null) {
                    throw new BusinessRuleException("Say whether " + name + " is a must-have or preferred.");
                }
            }
            // Bean validation says the same on every HTTP path; this covers callers that skip it.
            Integer weight = criterion.weightPercent();
            if (weight != null && (weight < 1 || weight > 100)) {
                throw new BusinessRuleException("Give " + name + " a weight between 1% and 100%.");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new BusinessRuleException(name + " is listed twice. Weigh each skill, tool or experience once.");
            }
            criteria.add(new Wanted(category, name, criterion.weightPercent(),
                    category.hasRequirement() ? criterion.requirement() : null));
        }
        return criteria;
    }

    private IdealMatchingResponse describe(JobVacancy vacancy) {
        List<JobScreeningCriteria> rows = stored(vacancy);
        List<IdealMatchingResponse.Criterion> criteria = rows.stream()
                .map(row -> new IdealMatchingResponse.Criterion(row.getId(), row.getCategory(), row.getName(),
                        Objects.requireNonNullElse(percent(row.getWeight()), 0), row.getRequirement(),
                        row.getPosition()))
                .toList();
        int total = criteria.stream().mapToInt(IdealMatchingResponse.Criterion::weightPercent).sum();
        return new IdealMatchingResponse(
                vacancy.isIdealMatchingAvailable(),
                vacancy.isIdealMatchingEnabled(),
                criteria,
                total,
                vacancy.getMatchingDegreeTypes(),
                vacancy.getMatchingNiceToHaveCertifications());
    }

    private List<JobScreeningCriteria> stored(JobVacancy vacancy) {
        return criteriaRepository.findAllByJobIdOrderByPositionAscNameAsc(vacancy.getId());
    }

    private static List<String> namesWith(List<JobScreeningCriteria> criteria, MatchingRequirement requirement) {
        return criteria.stream()
                .filter(criterion -> criterion.getRequirement() == requirement)
                .map(JobScreeningCriteria::getName)
                .toList();
    }

    private static int total(List<Wanted> criteria) {
        return criteria.stream().map(Wanted::weightPercent).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    /** 25 → 0.250, the stored scale; {@code null} stays {@code null}. */
    private static BigDecimal fraction(Integer percent) {
        return percent == null ? null : BigDecimal.valueOf(percent).divide(HUNDRED, 3, RoundingMode.UNNECESSARY);
    }

    /** 0.250 → 25; {@code null} stays {@code null}. */
    private static Integer percent(BigDecimal fraction) {
        return fraction == null ? null : fraction.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static boolean isDegree(String education) {
        return education != null && DEGREE_LEVELS.contains(education.trim());
    }

    /** Trimmed, blanks dropped, repeats ignoring case removed, first spelling kept. */
    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isEmpty() && seen.add(cleaned.toLowerCase(Locale.ROOT))) {
                kept.add(cleaned);
            }
        }
        return List.copyOf(kept);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String joinNames(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** One criterion as it will be stored. */
    public record Wanted(MatchingCategory category, String name, Integer weightPercent,
                         MatchingRequirement requirement) {
    }
}
