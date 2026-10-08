package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobVacancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The public job board's search query, kept apart from the derived queries in
 * {@link JobVacancyRepository} because it cannot be one: PostgreSQL full-text
 * search has no JPQL spelling. Implemented by
 * {@link JobVacancySearchRepositoryImpl}; Spring Data weaves it into
 * {@code JobVacancyRepository}.
 */
public interface JobVacancySearchRepository {

    /**
     * Published vacancies across every tenant that match {@code criteria}.
     *
     * <p>Ordered by relevance when a keyword is given, otherwise newest first;
     * {@code publishedAt} then {@code id} break ties either way, so pages
     * never overlap or skip a row. Any {@code Sort} on {@code pageable} is
     * ignored — only its page and size are used.</p>
     */
    Page<JobVacancy> searchPublished(PublicJobSearchCriteria criteria, Pageable pageable);
}
