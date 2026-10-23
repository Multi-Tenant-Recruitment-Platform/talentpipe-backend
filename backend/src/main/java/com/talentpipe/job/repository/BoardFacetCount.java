package com.talentpipe.job.repository;

/**
 * Projection for "how many published vacancies have each value of this
 * field": one row per value.
 *
 * @param <T> the field's type, an enum such as {@code EmploymentType}
 */
public interface BoardFacetCount<T> {

    T getFacet();

    long getTotal();
}
