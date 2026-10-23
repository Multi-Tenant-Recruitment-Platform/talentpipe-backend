package com.talentpipe.job.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.repository.BoardFilters;
import com.talentpipe.job.repository.BoardSort;
import com.talentpipe.job.repository.PublicJobSearchCriteria;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Unit tests for turning the board's query string into search criteria. */
class PublicJobSearchParamsTest {

    @Test
    void nothingSet_isThePlainBoard() {
        assertThat(new PublicJobSearchParams().toCriteria()).isEqualTo(PublicJobSearchCriteria.none());
    }

    @Test
    void everythingSet_isCarriedOver_withTheCurrencyUpperCased_andRepeatsIgnored() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setQ(" java ");
        params.setIndustry("information-technology");
        params.setEmploymentType(Arrays.asList(EmploymentType.FULL_TIME, EmploymentType.FULL_TIME, null));
        params.setMinExperience(2);
        params.setSalaryOnly(true);
        params.setPayPeriod(PayPeriod.ANNUAL);
        params.setCurrency(" usd ");
        params.setSalaryMax(new BigDecimal("90000"));
        params.setSort("Newest");

        PublicJobSearchCriteria criteria = params.toCriteria();

        assertThat(criteria.keyword()).isEqualTo("java");
        assertThat(criteria.sort()).isEqualTo(BoardSort.NEWEST);
        BoardFilters filters = criteria.filters();
        assertThat(filters.industry()).isEqualTo("Information Technology");
        assertThat(filters.employmentTypes()).containsExactly(EmploymentType.FULL_TIME);
        assertThat(filters.minExperience()).isEqualTo(2);
        assertThat(filters.salary()).isEqualTo(new BoardFilters.Salary(
                true, PayPeriod.ANNUAL, "USD", null, new BigDecimal("90000")));
    }

    @Test
    void anExperienceRangeTheWrongWayRound_isRefused() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setMinExperience(5);
        params.setMaxExperience(2);

        assertThatThrownBy(params::toCriteria).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("minExperience");
    }

    @Test
    void aSalaryRangeTheWrongWayRound_isRefused() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setPayPeriod(PayPeriod.MONTHLY);
        params.setSalaryMin(new BigDecimal("200"));
        params.setSalaryMax(new BigDecimal("100"));

        assertThatThrownBy(params::toCriteria).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("salaryMin");
    }

    @Test
    void aSalaryRangeWithoutAPayPeriod_isRefused_payPeriodsDoNotCompare() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setSalaryMax(new BigDecimal("100"));

        assertThatThrownBy(params::toCriteria).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("payPeriod");
    }

    @Test
    void aCurrencyThatIsNotAThreeLetterCode_isRefused() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setCurrency("rupees");

        assertThatThrownBy(params::toCriteria).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void anIndustryThatIsNotListed_isRefused() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setIndustry("Space Tourism");

        assertThatThrownBy(params::toCriteria).isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("industry must be one of:");
    }

    @Test
    void anUnknownSort_isRefused() {
        PublicJobSearchParams params = new PublicJobSearchParams();
        params.setSort("cheapest");

        assertThatThrownBy(params::toCriteria).isInstanceOf(InvalidRequestException.class)
                .hasMessage("sort must be relevant or newest.");
    }
}
