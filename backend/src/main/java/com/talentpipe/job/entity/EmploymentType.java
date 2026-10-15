package com.talentpipe.job.entity;

/** Contract type of a vacancy — mirrors the CHECK constraint on job_vacancies.employment_type. */
public enum EmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    TEMPORARY
}
