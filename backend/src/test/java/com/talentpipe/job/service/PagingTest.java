package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Unit tests for {@link Paging}: whatever the client sends, the result is safe to run. */
class PagingTest {

    @Test
    void inRangeValues_passThroughUnchanged() {
        Pageable pageable = Paging.of(3, 25, 100, Sort.unsorted());

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void negativePage_becomesTheFirstPage() {
        assertThat(Paging.of(-5, 20, 100, Sort.unsorted()).getPageNumber()).isZero();
    }

    @Test
    void sizeAboveTheMaximum_isCappedToIt() {
        assertThat(Paging.of(0, 1_000_000, 100, Sort.unsorted()).getPageSize()).isEqualTo(100);
    }

    @Test
    void zeroOrNegativeSize_becomesOne() {
        assertThat(Paging.of(0, 0, 100, Sort.unsorted()).getPageSize()).isEqualTo(1);
        assertThat(Paging.of(0, -10, 100, Sort.unsorted()).getPageSize()).isEqualTo(1);
    }

    @Test
    void absurdPage_isCapped_soTheOffsetAlwaysFitsAnInt() {
        Pageable pageable = Paging.of(Integer.MAX_VALUE, 100, 100, Sort.unsorted());

        assertThat(pageable.getPageNumber()).isEqualTo(Paging.MAX_PAGE);
        assertThat(pageable.getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void theGivenSortIsKept() {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"));

        assertThat(Paging.of(0, 20, 100, sort).getSort()).isEqualTo(sort);
    }
}
