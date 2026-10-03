package com.talentpipe.job.entity;

/** The period a salary range is quoted for — mirrors the CHECK constraint on job_vacancies.pay_period. */
public enum PayPeriod {
    HOURLY,
    MONTHLY,
    ANNUAL
}
