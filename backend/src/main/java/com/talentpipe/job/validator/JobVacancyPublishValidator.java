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
                today
        );
    }

    public void validateForPublish(JobVacancy vacancy, LocalDate today) {
        VacancyContent content = vacancy.content();
        validatePublishFields(
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
            LocalDate today
    ) {
        List<String> missing = new ArrayList<>();

        if (isBlank(title)) {
            missing.add("a job title");
        }
        if (isBlank(department)) {
            missing.add("a department");
        }
        if (openings == null || openings < 1) {
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
        if (applicationDeadline == null || applicationDeadline.isBefore(today)) {
            missing.add("an application deadline");
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

        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "This vacancy can't be published yet: add " + joinLabels(missing) + "."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isEmptyList(List<String> list) {
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
