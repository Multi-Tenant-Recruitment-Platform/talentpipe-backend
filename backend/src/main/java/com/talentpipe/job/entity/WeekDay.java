package com.talentpipe.job.entity;

/**
 * Working-day identifiers, Monday first. Stored by name in
 * {@code job_vacancies.working_days}; declaration order is the order a working
 * week is always written back in, whatever order the client sent.
 */
public enum WeekDay {
    MON,
    TUE,
    WED,
    THU,
    FRI,
    SAT,
    SUN
}
