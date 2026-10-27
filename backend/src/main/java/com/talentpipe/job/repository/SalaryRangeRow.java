package com.talentpipe.job.repository;

import java.math.BigDecimal;

/** Projection for one pay period and currency's span of stated pay on the board. */
public interface SalaryRangeRow {

    /** As stored: {@code HOURLY}, {@code MONTHLY} or {@code ANNUAL}. */
    String getPayPeriod();

    /** The three-letter code, upper case. */
    String getCurrency();

    BigDecimal getLowest();

    BigDecimal getHighest();

    long getTotal();
}
