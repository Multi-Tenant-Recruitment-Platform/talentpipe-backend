package com.talentpipe.job.repository;

import com.talentpipe.job.entity.JobVacancy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Runs the public board's search (PB-017) as native SQL, because PostgreSQL
 * full-text search has no JPQL spelling.
 *
 * <p>This class only executes. What is executed — the statement text and why
 * it is built the way it is — lives in {@link PublicJobSearchQuery}. No user
 * input is ever concatenated into SQL here or there: every value is bound.</p>
 *
 * <p>Rows come back as managed {@link JobVacancy} entities. {@code SELECT v.*}
 * also returns the {@code search_vector} column, which the entity does not
 * map; Hibernate reads the columns it knows by name and ignores the rest.</p>
 */
class JobVacancySearchRepositoryImpl implements JobVacancySearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Page<JobVacancy> searchPublished(PublicJobSearchCriteria criteria, Pageable pageable) {
        PublicJobSearchQuery search = PublicJobSearchQuery.of(criteria);

        Query rows = entityManager.createNativeQuery(search.selectSql(), JobVacancy.class);
        Query count = entityManager.createNativeQuery(search.countSql());
        search.parameters().forEach((name, value) -> {
            rows.setParameter(name, value);
            count.setParameter(name, value);
        });

        rows.setFirstResult(Math.toIntExact(pageable.getOffset()));
        rows.setMaxResults(pageable.getPageSize());

        List<JobVacancy> content = rows.getResultList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(content, pageable, total);
    }
}
