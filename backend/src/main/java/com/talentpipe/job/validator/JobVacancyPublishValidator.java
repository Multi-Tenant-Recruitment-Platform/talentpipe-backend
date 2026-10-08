package com.talentpipe.job.validator;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.WorkplaceType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates business prerequisites for transitioning a vacancy to PUBLISHED.
 *
 * <p>Collects all missing required fields and produces a single human-readable
 * 422 error sentence.</p>
 */
@Component
public class JobVacancyPublishValidator {

    public static final String NOT_READY_TO_PUBLISH = "This vacancy can't be published yet";
    public static final String MUST_STAY_COMPLETE = "A published vacancy has to stay complete";

    public void validateForPublish(JobVacancyRequest request, LocalDate today) {
        validatePublishFields(
                request.title(),
                request.department(),
                request.openings(),
                request.employmentType(),
                request.workplaceType(),
                request.location(),
                request.applicationDeadline(),
                request.jobSummary(),
                request.jobDescription(),
                request.keyResponsibilities(),
                request.requiredSkills(),
                today,
                NOT_READY_TO_PUBLISH
        );
    }

    public void validateForStayComplete(JobVacancyRequest request, LocalDate today) {
        validatePublishFields(
                request.title(),
                request.department(),
                request.openings(),
                request.employmentType(),
                request.workplaceType(),
                request.location(),
                request.applicationDeadline(),
                request.jobSummary(),
                request.jobDescription(),
                request.keyResponsibilities(),
                request.requiredSkills(),
                today,
                MUST_STAY_COMPLETE
        );
    }

    public void validateForPublish(JobVacancy vacancy, LocalDate today) {
        VacancyContent content = vacancy.content();
        validateForPublish(content, today);
    }

    public void validateForPublish(VacancyContent content, LocalDate today) {
        requireComplete(content, today, NOT_READY_TO_PUBLISH);
    }

    public static void requireComplete(VacancyContent content, LocalDate today, String lead) {
        List<String> missing = missing(content, today);
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(lead + ": add " + joinLabels(missing) + ".");
        }
    }

    public static List<String> missing(VacancyContent content, LocalDate today) {
        return findMissing(
                content.title(),
                content.department(),
                content.openings(),
                content.employmentType(),
                content.workplaceType(),
                content.location(),
                content.applicationDeadline(),
                content.jobSummary(),
                content.jobDescription(),
                content.keyResponsibilities(),
                content.requiredSkills(),
                today
        );
    }

    private void validatePublishFields(
            String title,
            String department,
            Integer openings,
            EmploymentType employmentType,
            WorkplaceType workplaceType,
            String location,
            LocalDate applicationDeadline,
            String jobSummary,
            String jobDescription,
            List<String> keyResponsibilities,
            List<String> requiredSkills,
            LocalDate today,
            String lead
    ) {
        List<String> missing = findMissing(
                title, department, openings, employmentType, workplaceType,
                location, applicationDeadline, jobSummary, jobDescription,
                keyResponsibilities, requiredSkills, today
        );

        if (!missing.isEmpty()) {
            throw new BusinessRuleException(lead + ": add " + joinLabels(missing) + ".");
        }
    }

    private static List<String> findMissing(
            String title,
            String department,
            Integer openings,
            EmploymentType employmentType,
            WorkplaceType workplaceType,
            String location,
            LocalDate applicationDeadline,
            String jobSummary,
            String jobDescription,
            List<String> keyResponsibilities,
            List<String> requiredSkills,
            LocalDate today
    ) {
        List<String> missing = new ArrayList<>();

        if (isBlank(title)) {
            missing.add("a job title");
        }
        if (isBlank(department)) {
            missing.add("a department");
        }
        if (openings != null && openings < 1) {
            missing.add("the number of openings");
        }
        if (employmentType == null) {
            missing.add("an employment type");
        }
        if (workplaceType == null) {
            missing.add("a workplace type");
        }
        if (isBlank(location)) {
            missing.add("a location");
        }
        if (applicationDeadline == null) {
            missing.add("an application deadline");
        } else if (applicationDeadline.isBefore(today)) {
            missing.add("an application deadline that is today or later");
        }
        if (isBlank(jobSummary)) {
            missing.add("a job summary");
        }
        if (isBlank(jobDescription)) {
            missing.add("a job description");
        }
        if (isEmptyList(keyResponsibilities)) {
            missing.add("at least one key responsibility");
        }
        if (isEmptyList(requiredSkills)) {
            missing.add("at least one required skill");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isEmptyList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return true;
        }
        return list.stream().noneMatch(item -> item != null && !item.trim().isEmpty());
    }

    public static String joinLabels(List<String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        if (labels.size() == 1) {
            return labels.get(0);
        }
        if (labels.size() == 2) {
            return labels.get(0) + " and " + labels.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.size() - 1; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(labels.get(i));
        }
        sb.append(" and ").append(labels.get(labels.size() - 1));
        return sb.toString();
    }
}
