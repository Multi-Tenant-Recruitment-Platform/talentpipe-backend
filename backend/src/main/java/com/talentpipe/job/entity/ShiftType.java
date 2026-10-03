package com.talentpipe.job.entity;

/** Shift pattern of a vacancy — mirrors the CHECK constraint on job_vacancies.shift_type. */
public enum ShiftType {
    DAY,
    NIGHT,
    ROTATING,
    FLEXIBLE
}
