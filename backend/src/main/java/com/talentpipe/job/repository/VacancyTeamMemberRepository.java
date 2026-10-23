package com.talentpipe.job.repository;

import com.talentpipe.job.entity.VacancyTeamMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancyTeamMemberRepository
        extends JpaRepository<VacancyTeamMember, VacancyTeamMember.Key> {

    /** A vacancy's hiring team, longest-standing member first. */
    List<VacancyTeamMember> findAllByVacancyIdOrderByAddedAtAscUserIdAsc(UUID vacancyId);
}
