package com.talentpipe.job.service;

import com.talentpipe.auth.dto.MemberSummary;
import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.dto.InterviewPanelRequest;
import com.talentpipe.job.dto.InterviewPanelResponse;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.entity.JobStageInterviewer;
import com.talentpipe.job.entity.JobStagePanel;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.repository.JobStageInterviewerRepository;
import com.talentpipe.job.repository.JobStagePanelRepository;
import com.talentpipe.job.repository.JobStageRepository;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.entity.StageType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who interviews in each interview round of a vacancy (V25).
 *
 * <p>A round's panel is a number — how many interviewers the round needs —
 * and the people chosen for it. Anyone active in the company may sit on a
 * panel, whatever their role; candidates are not company members, so they
 * never can. Being on a panel lets someone see the vacancy (see
 * {@link VacancyAccess}); what they may do with it is still their role's
 * business.</p>
 *
 * <p>A draft may be saved with its panels half chosen. Publishing needs every
 * round to have exactly as many interviewers as its panel asks for, and a live
 * vacancy whose panels are changed must stay that way. Choosing more people
 * than the number, or people before a number, is refused in every state.</p>
 *
 * <p>Panels stay editable for as long as the hiring team does: until the
 * vacancy is archived. A closed vacancy is still interviewing its
 * applicants.</p>
 */
@Service
public class InterviewPanelService {

    private static final Logger log = LoggerFactory.getLogger(InterviewPanelService.class);

    /** Said in front of what is missing when a vacancy is published. */
    public static final String NOT_READY_TO_PUBLISH = "This vacancy can't be published yet";

    /** Said in front of what is missing when a live vacancy's panels are changed. */
    public static final String MUST_STAY_COMPLETE = "A published vacancy needs every interview panel complete";

    static final String NOT_AN_INTERVIEW_ROUND = "Panels can only be set for this vacancy's interview rounds.";
    static final String ROUND_LISTED_TWICE = "Each interview round can have only one panel.";
    static final String NOT_ACTIVE_MEMBERS =
            "Everyone you add to an interview panel must be an active member of your workspace.";

    private final VacancyAccess access;
    private final JobStageRepository stageRepository;
    private final JobStagePanelRepository panelRepository;
    private final JobStageInterviewerRepository interviewerRepository;
    private final UserDirectoryService userDirectory;

    public InterviewPanelService(VacancyAccess access,
                                 JobStageRepository stageRepository,
                                 JobStagePanelRepository panelRepository,
                                 JobStageInterviewerRepository interviewerRepository,
                                 UserDirectoryService userDirectory) {
        this.access = access;
        this.stageRepository = stageRepository;
        this.panelRepository = panelRepository;
        this.interviewerRepository = interviewerRepository;
        this.userDirectory = userDirectory;
    }

    // ------------------------------------------------------------------ reads

    /**
     * Every interview round of the vacancy, in pipeline order, with its panel.
     * A round nobody has staffed yet is listed with no count and nobody on it.
     *
     * @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see
     */
    @Transactional(readOnly = true)
    public List<InterviewPanelResponse> get(VacancyViewer viewer, UUID vacancyId) {
        return describe(access.require(viewer, vacancyId));
    }

    // ----------------------------------------------------------------- writes

    /**
     * Replaces every panel of the vacancy with the ones given. A round left
     * out of the list, or sent with no count and nobody, loses its panel.
     *
     * @throws com.talentpipe.common.exception.ResourceNotFoundException (404) not the viewer's to see
     * @throws BusinessRuleException (422) the vacancy is archived; a panel
     *         names something other than one of its interview rounds, or the
     *         same round twice; it chooses people without a number, or more
     *         people than the number; someone added is not an active member;
     *         or the vacancy is live and a panel would be left incomplete
     */
    @Transactional
    public List<InterviewPanelResponse> replace(VacancyViewer viewer, UUID vacancyId,
                                                List<InterviewPanelRequest> panels) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        vacancy.requireTeamEditable();
        apply(vacancy, panels, viewer.userId());
        if (vacancy.getStatus() == VacancyStatus.PUBLISHED) {
            requireComplete(vacancy, MUST_STAY_COMPLETE);
        }
        return describe(vacancy);
    }

    /**
     * Writes the vacancy's panels as given — the work behind {@link #replace},
     * and how a create or an edit that carries panels stores them, in its own
     * transaction. Says nothing about completeness: that is the caller's
     * decision, because a draft may be incomplete.
     *
     * @throws BusinessRuleException (422) as {@link #replace}, completeness aside
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(JobVacancy vacancy, List<InterviewPanelRequest> panels, UUID actorId) {
        UUID tenantId = vacancy.getTenantId();
        Map<Integer, JobStage> rounds = interviewRounds(vacancy.getId());
        Map<UUID, JobStagePanel> stored = panelRepository.findAllByJobId(vacancy.getId()).stream()
                .collect(Collectors.toMap(JobStagePanel::getJobStageId, Function.identity()));
        Map<UUID, List<JobStageInterviewer>> rows = rowsBy(vacancy.getId());
        Map<UUID, List<UUID>> seated = idsOf(rows);

        // Read every request first, so a refusal changes nothing.
        Map<UUID, Wanted> wanted = new LinkedHashMap<>();
        Set<UUID> newcomers = new LinkedHashSet<>();
        for (InterviewPanelRequest panel : panels) {
            JobStage round = rounds.get(panel.stagePosition());
            if (round == null) {
                throw new BusinessRuleException(NOT_AN_INTERVIEW_ROUND);
            }
            if (wanted.containsKey(round.getId())) {
                throw new BusinessRuleException(ROUND_LISTED_TWICE);
            }
            List<UUID> people = List.copyOf(new LinkedHashSet<>(panel.interviewers()));
            requireFits(round.getName(), panel.interviewerCount(), people.size());
            wanted.put(round.getId(), new Wanted(panel.interviewerCount(), people));

            Set<UUID> already = new HashSet<>(seated.getOrDefault(round.getId(), List.of()));
            people.stream().filter(person -> !already.contains(person)).forEach(newcomers::add);
        }
        // Only people being added must be active now: someone already on a
        // panel who has since been disabled stays until they are taken off,
        // the same rule as the hiring team's.
        if (!userDirectory.activeStaffAmong(tenantId, newcomers).containsAll(newcomers)) {
            throw new BusinessRuleException(NOT_ACTIVE_MEMBERS);
        }

        int added = 0;
        int removed = 0;
        for (JobStage round : rounds.values()) {
            Wanted panel = wanted.get(round.getId());
            List<UUID> before = seated.getOrDefault(round.getId(), List.of());
            List<UUID> after = panel == null || panel.count() == null ? List.of() : panel.people();

            List<JobStageInterviewer> leaving = rows.getOrDefault(round.getId(), List.of()).stream()
                    .filter(row -> !after.contains(row.getUserId()))
                    .toList();
            interviewerRepository.deleteAll(leaving);
            removed += leaving.size();

            JobStagePanel current = stored.get(round.getId());
            if (panel == null || panel.count() == null) {
                if (current != null) {
                    // The interviewers go first: they reference the panel.
                    interviewerRepository.flush();
                    panelRepository.delete(current);
                }
                continue;
            }
            if (current == null) {
                panelRepository.save(new JobStagePanel(tenantId, vacancy.getId(), round.getId(), panel.count(),
                        actorId));
            } else if (current.getInterviewerCount() != panel.count()) {
                current.resize(panel.count(), actorId);
            }
            // Flushed so the panel row exists before its interviewers reference it.
            panelRepository.flush();
            for (UUID person : after) {
                if (!before.contains(person)) {
                    interviewerRepository.save(
                            new JobStageInterviewer(tenantId, vacancy.getId(), round.getId(), person, actorId));
                    added++;
                }
            }
        }
        // Flushed here so a refused write fails this call, not the commit after it.
        interviewerRepository.flush();
        panelRepository.flush();
        log.info("Vacancy {} interview panels changed: {} interviewers added, {} removed [tenant={}, user={}]",
                vacancy.getId(), added, removed, tenantId, actorId);
    }

    /**
     * Gives a duplicate the source's interview panels, round by round, leaving
     * out anyone no longer an active member — as its hiring team is copied
     * ({@code VacancyHiringTeamService#copyTeam}). Call after the copy's
     * stages are stored. A round whose people all left keeps its count and
     * waits to be staffed again before the copy can be published.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void copyPanels(JobVacancy source, JobVacancy copy, UUID actorId) {
        Map<UUID, Integer> counts = panelRepository.findAllByJobId(source.getId()).stream()
                .collect(Collectors.toMap(JobStagePanel::getJobStageId, JobStagePanel::getInterviewerCount));
        if (counts.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> seated = seatedBy(source.getId());
        Set<UUID> everyone = new LinkedHashSet<>();
        seated.values().forEach(everyone::addAll);
        Set<UUID> stillActive = userDirectory.activeStaffAmong(copy.getTenantId(), everyone);

        List<InterviewPanelRequest> panels = new ArrayList<>();
        for (JobStage round : interviewRounds(source.getId()).values()) {
            Integer count = counts.get(round.getId());
            if (count != null) {
                List<UUID> people = seated.getOrDefault(round.getId(), List.of()).stream()
                        .filter(stillActive::contains)
                        .toList();
                panels.add(new InterviewPanelRequest(round.getPosition(), count, people));
            }
        }
        apply(copy, panels, actorId);
    }

    // ------------------------------------------------------------ completeness

    /**
     * Refuses a stored vacancy whose interview rounds are not all fully
     * staffed. A vacancy without interview rounds — one older than pipeline
     * templates, say — has nothing to staff and passes.
     *
     * @throws BusinessRuleException (422) naming every round that is short
     */
    @Transactional(readOnly = true)
    public void requireComplete(JobVacancy vacancy, String lead) {
        Map<UUID, Integer> counts = panelRepository.findAllByJobId(vacancy.getId()).stream()
                .collect(Collectors.toMap(JobStagePanel::getJobStageId, JobStagePanel::getInterviewerCount));
        Map<UUID, List<UUID>> seated = seatedBy(vacancy.getId());
        List<String> problems = new ArrayList<>();
        for (JobStage round : interviewRounds(vacancy.getId()).values()) {
            shortfall(round.getName(), counts.get(round.getId()),
                    seated.getOrDefault(round.getId(), List.of()).size()).ifPresent(problems::add);
        }
        refuseIfShort(problems, lead);
    }

    /**
     * {@link #requireComplete} for a vacancy that is not stored yet: its rounds
     * are the template's, its panels the ones the create request carries.
     *
     * @throws BusinessRuleException (422) naming every round that is short
     */
    public static void requireComplete(List<PipelineStageResponse> stages, List<InterviewPanelRequest> panels,
                                       String lead) {
        Map<Integer, InterviewPanelRequest> byPosition = new HashMap<>();
        if (panels != null) {
            panels.forEach(panel -> byPosition.putIfAbsent(panel.stagePosition(), panel));
        }
        List<String> problems = new ArrayList<>();
        stages.stream()
                .filter(stage -> stage.stageType() == StageType.INTERVIEW)
                .sorted(Comparator.comparingInt(PipelineStageResponse::position))
                .forEach(stage -> {
                    InterviewPanelRequest panel = byPosition.get(stage.position());
                    Integer count = panel == null ? null : panel.interviewerCount();
                    int chosen = panel == null ? 0 : new LinkedHashSet<>(panel.interviewers()).size();
                    shortfall(stage.name(), count, chosen).ifPresent(problems::add);
                });
        refuseIfShort(problems, lead);
    }

    /** What a round is short of, or nothing when it has exactly the interviewers it needs. */
    static Optional<String> shortfall(String round, Integer count, int chosen) {
        if (count == null) {
            return Optional.of(round + " needs its interviewers");
        }
        if (chosen != count) {
            return Optional.of(round + " needs " + count + " " + interviewers(count) + ", "
                    + chosen + " chosen");
        }
        return Optional.empty();
    }

    private static void refuseIfShort(List<String> problems, String lead) {
        if (!problems.isEmpty()) {
            throw new BusinessRuleException(lead + ": " + String.join("; ", problems) + ".");
        }
    }

    // ---------------------------------------------------------------- private

    /** Refuses a panel that could never be right, whatever the vacancy's state. */
    private static void requireFits(String round, Integer count, int chosen) {
        if (count == null && chosen > 0) {
            throw new BusinessRuleException(
                    "Set how many interviewers " + round + " needs before choosing them.");
        }
        if (count != null && chosen > count) {
            throw new BusinessRuleException(round + " has " + chosen + " " + interviewers(chosen)
                    + " but needs only " + count + ". Remove some or raise the number.");
        }
    }

    private static String interviewers(int count) {
        return count == 1 ? "interviewer" : "interviewers";
    }

    /** The vacancy's interview rounds by position, first to last. */
    private Map<Integer, JobStage> interviewRounds(UUID vacancyId) {
        Map<Integer, JobStage> rounds = new LinkedHashMap<>();
        stageRepository.findAllByJobIdOrderByPositionAsc(vacancyId).stream()
                .filter(stage -> stage.getStageType() == StageType.INTERVIEW)
                .forEach(stage -> rounds.put(stage.getPosition(), stage));
        return rounds;
    }

    /** The rows of everyone on the vacancy's panels, keyed by stage, in the order they were added. */
    private Map<UUID, List<JobStageInterviewer>> rowsBy(UUID vacancyId) {
        Map<UUID, List<JobStageInterviewer>> rows = new HashMap<>();
        interviewerRepository.findAllByJobIdOrderByAddedAtAscUserIdAsc(vacancyId).forEach(row ->
                rows.computeIfAbsent(row.getJobStageId(), stage -> new ArrayList<>()).add(row));
        return rows;
    }

    /** Who sits on each of the vacancy's panels, keyed by stage, in the order they were added. */
    private Map<UUID, List<UUID>> seatedBy(UUID vacancyId) {
        return idsOf(rowsBy(vacancyId));
    }

    private static Map<UUID, List<UUID>> idsOf(Map<UUID, List<JobStageInterviewer>> rows) {
        Map<UUID, List<UUID>> ids = new HashMap<>();
        rows.forEach((stage, seats) -> ids.put(stage, seats.stream().map(JobStageInterviewer::getUserId).toList()));
        return ids;
    }

    private List<InterviewPanelResponse> describe(JobVacancy vacancy) {
        Map<Integer, JobStage> rounds = interviewRounds(vacancy.getId());
        Map<UUID, Integer> counts = panelRepository.findAllByJobId(vacancy.getId()).stream()
                .collect(Collectors.toMap(JobStagePanel::getJobStageId, JobStagePanel::getInterviewerCount));
        Map<UUID, List<UUID>> seated = seatedBy(vacancy.getId());

        Set<UUID> everyone = new LinkedHashSet<>();
        seated.values().forEach(everyone::addAll);
        Map<UUID, MemberSummary> people = userDirectory.describe(vacancy.getTenantId(), everyone);

        return rounds.values().stream()
                .map(round -> new InterviewPanelResponse(
                        round.getId(),
                        round.getPosition(),
                        round.getName(),
                        round.getBenchmark(),
                        counts.get(round.getId()),
                        seated.getOrDefault(round.getId(), List.of()).stream()
                                .map(people::get)
                                .filter(Objects::nonNull)
                                .toList()))
                .toList();
    }

    /** One round's panel as a request asked for it, repeats removed. */
    private record Wanted(Integer count, List<UUID> people) {
    }
}
