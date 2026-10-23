package com.talentpipe.job.repository;

import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.WorkplaceType;
import java.math.BigDecimal;
import java.util.Set;

/**
 * The job board's structured filters, next to the free-text ones in
 * {@link PublicJobSearchCriteria}. Each part left empty or {@code null} does
 * not filter. Build instances already validated; the query trusts them.
 *
 * @param employmentTypes any of these; empty for any
 * @param workplaceTypes  any of these; empty for any
 * @param minExperience   at least this many years asked for; a vacancy that
 *                        names none asks for none (0)
 * @param maxExperience   at most this many years asked for
 * @param salary          what the vacancy must say about pay
 */
public record BoardFilters(
        Set<EmploymentType> employmentTypes,
        Set<WorkplaceType> workplaceTypes,
        Integer minExperience,
        Integer maxExperience,
        Salary salary
) {

    /** Filters on nothing. */
    public static final BoardFilters NONE = new BoardFilters(Set.of(), Set.of(), null, null, Salary.ANY);

    public BoardFilters {
        employmentTypes = employmentTypes == null ? Set.of() : Set.copyOf(employmentTypes);
        workplaceTypes = workplaceTypes == null ? Set.of() : Set.copyOf(workplaceTypes);
        salary = salary == null ? Salary.ANY : salary;
    }

    /**
     * What the vacancy must say about pay.
     *
     * <p>Pay is only comparable within one pay period (and, in practice, one
     * currency), so a range ({@code min}/{@code max}) always comes with a
     * {@code payPeriod}. A vacancy matches a range when the pay it states
     * overlaps it; one that states a single figure is a range of one point.</p>
     *
     * @param statedOnly only vacancies that state a salary at all
     * @param payPeriod  only this pay period
     * @param currency   only this currency code, e.g. {@code LKR}, upper case
     * @param min        overlapping a range from this amount
     * @param max        overlapping a range up to this amount
     */
    public record Salary(boolean statedOnly, PayPeriod payPeriod, String currency, BigDecimal min, BigDecimal max) {

        public static final Salary ANY = new Salary(false, null, null, null, null);
    }
}
