package com.talentpipe.job.repository;

import com.talentpipe.job.entity.VacancyStatus;

/** One row of {@link JobVacancyRepository#countByStatus}: how many vacancies a tenant has in a state. */
public interface VacancyStatusCount {

    VacancyStatus getStatus();

    long getTotal();
}
