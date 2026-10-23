package com.talentpipe.job.dto;

import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.WorkplaceType;
import com.talentpipe.job.repository.BoardFilters;
import com.talentpipe.job.repository.BoardSort;
import com.talentpipe.job.repository.PublicJobSearchCriteria;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The public job board's query string, bound by name. Every parameter is
 * optional and they combine with AND.
 *
 * <p>A JavaBean rather than a record so Spring binds it from request
 * parameters; list parameters may be repeated ({@code ?employmentType=A&employmentType=B})
 * or comma-separated ({@code ?employmentType=A,B}).</p>
 */
public class PublicJobSearchParams {

    /** Largest number of years a vacancy can ask for (the V12 column check). */
    static final int MAX_YEARS = 50;

    private String q;
    private String category;
    private String location;
    private List<EmploymentType> employmentType;
    private List<WorkplaceType> workplaceType;

    @Min(value = 0, message = "Experience can't be negative.")
    @Max(value = MAX_YEARS, message = "Experience can be at most " + MAX_YEARS + " years.")
    private Integer minExperience;

    @Min(value = 0, message = "Experience can't be negative.")
    @Max(value = MAX_YEARS, message = "Experience can be at most " + MAX_YEARS + " years.")
    private Integer maxExperience;

    private boolean salaryOnly;
    private PayPeriod payPeriod;
    private String currency;

    @DecimalMin(value = "0", message = "A salary can't be negative.")
    private BigDecimal salaryMin;

    @DecimalMin(value = "0", message = "A salary can't be negative.")
    private BigDecimal salaryMax;

    private String sort;

    /**
     * The criteria the repository searches with.
     *
     * @throws InvalidRequestException (400) the parameters contradict each
     *         other, or {@code currency} or {@code sort} is not a known value
     */
    public PublicJobSearchCriteria toCriteria() {
        if (minExperience != null && maxExperience != null && minExperience > maxExperience) {
            throw new InvalidRequestException("minExperience can't be more than maxExperience.");
        }
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw new InvalidRequestException("salaryMin can't be more than salaryMax.");
        }
        if ((salaryMin != null || salaryMax != null) && payPeriod == null) {
            throw new InvalidRequestException(
                    "Choose a payPeriod with a salary range: hourly, monthly and annual pay don't compare.");
        }
        BoardSort order = sort == null || sort.isBlank()
                ? BoardSort.RELEVANT
                : BoardSort.parse(sort).orElseThrow(() ->
                        new InvalidRequestException("sort must be relevant or newest."));

        BoardFilters filters = new BoardFilters(
                asSet(employmentType, EmploymentType.class),
                asSet(workplaceType, WorkplaceType.class),
                minExperience,
                maxExperience,
                new BoardFilters.Salary(salaryOnly, payPeriod, currencyCode(currency), salaryMin, salaryMax));
        return PublicJobSearchCriteria.of(q, category, location, filters, order);
    }

    /** A three-letter code such as {@code LKR}, upper-cased; blank means any. */
    private static String currencyCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        boolean threeLetters = code.length() == 3 && code.chars().allMatch(c -> c >= 'A' && c <= 'Z');
        if (!threeLetters) {
            throw new InvalidRequestException("currency must be a three-letter code, such as LKR.");
        }
        return code;
    }

    private static <E extends Enum<E>> Set<E> asSet(List<E> values, Class<E> type) {
        Set<E> set = EnumSet.noneOf(type);
        if (values != null) {
            values.stream().filter(Objects::nonNull).forEach(set::add);
        }
        return set;
    }

    // ------------------------------------------------------- binding accessors

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<EmploymentType> getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(List<EmploymentType> employmentType) {
        this.employmentType = employmentType;
    }

    public List<WorkplaceType> getWorkplaceType() {
        return workplaceType;
    }

    public void setWorkplaceType(List<WorkplaceType> workplaceType) {
        this.workplaceType = workplaceType;
    }

    public Integer getMinExperience() {
        return minExperience;
    }

    public void setMinExperience(Integer minExperience) {
        this.minExperience = minExperience;
    }

    public Integer getMaxExperience() {
        return maxExperience;
    }

    public void setMaxExperience(Integer maxExperience) {
        this.maxExperience = maxExperience;
    }

    public boolean isSalaryOnly() {
        return salaryOnly;
    }

    public void setSalaryOnly(boolean salaryOnly) {
        this.salaryOnly = salaryOnly;
    }

    public PayPeriod getPayPeriod() {
        return payPeriod;
    }

    public void setPayPeriod(PayPeriod payPeriod) {
        this.payPeriod = payPeriod;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal salaryMin) {
        this.salaryMin = salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }
}
