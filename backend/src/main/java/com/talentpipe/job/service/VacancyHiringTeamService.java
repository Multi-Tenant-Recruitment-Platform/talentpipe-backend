package com.talentpipe.job.service;

import com.talentpipe.auth.dto.MemberSummary;
import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.dto.HiringTeamRequest;
import com.talentpipe.job.dto.HiringTeamResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyTeamMember;
import com.talentpipe.job.repository.VacancyTeamMemberRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A vacancy's hiring team: the people who work on it besides its assigned
 * recruiter and hiring manager — interviewers, a second recruiter, someone
 * from the business who reviews applicants.
 *
 * <p>Being on the team is what lets a member whose role lacks
 * {@code jobs.view.all} see the vacancy at all (see {@link VacancyAccess}).
 * It grants nothing else: what they may do with the vacancy is still decided
 * by their role.</p>
 */
@Service
public class VacancyHiringTeamService {

    private static final Logger log = LoggerFactory.getLogger(VacancyHiringTeamService.class);

    private final VacancyAccess access;
    private final VacancyTeamMemberRepository teamRepository;
    private final UserDirectoryService userDirectory;

    public VacancyHiringTeamService(VacancyAccess access,
                                    VacancyTeamMemberRepository teamRepository,
                                    UserDirectoryService userDirectory) {
        this.access = access;
        this.teamRepository = teamRepository;
        this.userDirectory = userDirectory;
    }

    /** @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see */
    @Transactional(readOnly = true)
    public HiringTeamResponse get(VacancyViewer viewer, UUID vacancyId) {
        return describe(access.require(viewer, vacancyId));
    }

    /**
     * Replaces the hiring team with the given people.
     *
     * <p>Only people being added must be active members of the workspace.
     * Someone already on the team who has since been disabled stays until they
     * are taken off, the same rule as for the recruiter and hiring manager,
     * so an unrelated change to the team never fails because of them.</p>
     *
     * @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see
     * @throws BusinessRuleException (422) the vacancy is archived, or someone
     *         being added is not an active member of the workspace
     */
    @Transactional
    public HiringTeamResponse replace(VacancyViewer viewer, UUID vacancyId, HiringTeamRequest request) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        vacancy.requireTeamEditable();

        Set<UUID> wanted = new LinkedHashSet<>(request.memberIds());
        List<VacancyTeamMember> current = teamRepository.findAllByVacancyIdOrderByAddedAtAscUserIdAsc(vacancyId);
        Set<UUID> currentIds = new HashSet<>();
        current.forEach(member -> currentIds.add(member.getUserId()));

        Set<UUID> added = new LinkedHashSet<>(wanted);
        added.removeAll(currentIds);
        if (!userDirectory.activeMembersAmong(viewer.tenantId(), added).containsAll(added)) {
            throw new BusinessRuleException(
                    "Everyone you add to the hiring team must be an active member of your workspace.");
        }

        List<VacancyTeamMember> removed = current.stream()
                .filter(member -> !wanted.contains(member.getUserId()))
                .toList();
        teamRepository.deleteAll(removed);
        added.forEach(userId -> teamRepository.save(
                new VacancyTeamMember(vacancyId, userId, viewer.tenantId(), viewer.userId())));
        teamRepository.flush();

        log.info("Vacancy {} hiring team changed: {} added, {} removed [tenant={}, user={}]",
                vacancyId, added.size(), removed.size(), viewer.tenantId(), viewer.userId());
        return describe(vacancy);
    }

    /**
     * Gives a duplicated vacancy the source's hiring team, leaving out anyone
     * who is no longer an active member — as the copy's recruiter and hiring
     * manager are (see {@code VacancyContent.asDuplicate}).
     */
    @Transactional
    public void copyTeam(UUID tenantId, UUID sourceId, UUID copyId, UUID actorId) {
        List<UUID> sourceTeam = teamRepository.findAllByVacancyIdOrderByAddedAtAscUserIdAsc(sourceId).stream()
                .map(VacancyTeamMember::getUserId)
                .toList();
        Set<UUID> stillActive = userDirectory.activeMembersAmong(tenantId, sourceTeam);
        sourceTeam.stream()
                .filter(stillActive::contains)
                .forEach(userId -> teamRepository.save(new VacancyTeamMember(copyId, userId, tenantId, actorId)));
    }

    // ---------------------------------------------------------------- private

    private HiringTeamResponse describe(JobVacancy vacancy) {
        VacancyContent content = vacancy.content();
        List<UUID> memberIds = teamRepository.findAllByVacancyIdOrderByAddedAtAscUserIdAsc(vacancy.getId()).stream()
                .map(VacancyTeamMember::getUserId)
                .toList();

        List<UUID> everyone = new ArrayList<>(memberIds);
        everyone.add(content.assignedRecruiterId());
        everyone.add(content.hiringManagerId());
        everyone.removeIf(Objects::isNull);
        Map<UUID, MemberSummary> people = userDirectory.describe(vacancy.getTenantId(), everyone);

        return new HiringTeamResponse(
                vacancy.getId(),
                personOrNull(people, content.assignedRecruiterId()),
                personOrNull(people, content.hiringManagerId()),
                memberIds.stream().map(people::get).filter(Objects::nonNull).toList());
    }

    private static MemberSummary personOrNull(Map<UUID, MemberSummary> people, UUID userId) {
        return userId == null ? null : people.get(userId);
    }
}
