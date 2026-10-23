package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.dto.RoleRequest;
import com.talentpipe.auth.dto.RoleResponse;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleAuditAction;
import com.talentpipe.auth.entity.RoleAuditEvent;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.RoleAuditEventRepository;
import com.talentpipe.auth.repository.RoleRepository;
import com.talentpipe.auth.repository.TenantRoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.security.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link TenantRoleService}: the guardrails that make
 * company-defined roles safe — no privilege escalation, the last Owner, roles
 * in use, optimistic locking — and the audit trail.
 */
@ExtendWith(MockitoExtension.class)
class TenantRoleServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Mock
    private TenantRoleRepository roleRepository;
    @Mock
    private RoleRepository legacyRoleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleAuditEventRepository auditRepository;
    @Mock
    private RolePermissionResolver resolver;

    @Captor
    private ArgumentCaptor<RoleAuditEvent> audit;

    private TenantRoleService service;

    @BeforeEach
    void setUp() {
        service = new TenantRoleService(roleRepository, legacyRoleRepository, userRepository, auditRepository,
                resolver, new HtmlSanitizer(), new UserMapper(),
                Clock.fixed(Instant.parse("2026-10-23T09:00:00Z"), ZoneOffset.UTC));
        lenient().when(roleRepository.saveAndFlush(any(TenantRole.class))).thenAnswer(call -> persisted(call.getArgument(0)));
        lenient().when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        for (RoleName name : RoleName.values()) {
            Role legacy = mock(Role.class);
            lenient().when(legacy.getName()).thenReturn(name);
            lenient().when(legacyRoleRepository.findByName(name)).thenReturn(Optional.of(legacy));
        }
    }

    // --------------------------------------------------------------- catalogue

    @Test
    void catalogue_listsEveryPermissionOnce_groupedInDisplayOrder() {
        var groups = service.permissionCatalogue();

        assertThat(groups).extracting(group -> group.key())
                .containsExactly("TEAM", "COMPANY", "VACANCIES", "HIRING", "INTERVIEWS");
        assertThat(groups.stream().mapToInt(group -> group.permissions().size()).sum())
                .isEqualTo(Permission.values().length);
        var publish = groups.get(2).permissions().stream()
                .filter(item -> item.key().equals("jobs.publish")).findFirst().orElseThrow();
        assertThat(publish.requires()).containsExactly("jobs.manage");
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_storesTheRoleWithItsRequirements_andAuditsIt() {
        actorHas(Permission.all());

        RoleResponse created = service.createRole(TENANT, ACTOR, request("Hiring Coordinator",
                List.of("team.invite", "interviews.schedule"), null));

        assertThat(created.name()).isEqualTo("Hiring Coordinator");
        assertThat(created.permissions()).containsExactly("team.view", "team.invite", "interviews.schedule");
        assertThat(created.systemRole()).isNull();
        assertThat(created.deletable()).isTrue();
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(RoleAuditAction.ROLE_CREATED);
        assertThat(audit.getValue().getActorUserId()).isEqualTo(ACTOR);
    }

    @Test
    void create_cannotGrantWhatTheCreatorDoesNotHold() {
        actorHas(EnumSet.of(Permission.TEAM_VIEW, Permission.ROLES_MANAGE));

        assertThatThrownBy(() -> service.createRole(TENANT, ACTOR,
                request("Publisher", List.of("jobs.publish"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("You can only give out permissions you have yourself. "
                        + "You don't have: Create and edit vacancies, Publish vacancies.");
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_withAnUnknownPermission_isABadRequest() {
        assertThatThrownBy(() -> service.createRole(TENANT, ACTOR, request("X", List.of("jobs.delete"), null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown permission 'jobs.delete'");
    }

    @Test
    void create_withATakenName_isAConflict_ignoringCase() {
        when(roleRepository.existsByTenantIdAndNameIgnoreCase(TENANT, "recruiter")).thenReturn(true);

        assertThatThrownBy(() -> service.createRole(TENANT, ACTOR, request("  recruiter ", List.of(), null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("There is already a role called 'recruiter'. Choose another name.");
    }

    @Test
    void create_stripsMarkupFromTheName_andRefusesANameThatIsOnlyMarkup() {
        actorHas(Permission.all());

        assertThat(service.createRole(TENANT, ACTOR, request("<b>Panel</b> member", List.of(), null)).name())
                .isEqualTo("Panel member");
        assertThatThrownBy(() -> service.createRole(TENANT, ACTOR, request("<i></i>", List.of(), null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void create_isCappedPerWorkspace() {
        when(roleRepository.countByTenantIdAndSystemRoleIsNull(TENANT)).thenReturn((long) TenantRoleService.MAX_CUSTOM_ROLES);

        assertThatThrownBy(() -> service.createRole(TENANT, ACTOR, request("One more", List.of(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("A workspace can have at most 50 custom roles.");
    }

    // ------------------------------------------------------------------ update

    @Test
    void update_replacesNameAndPermissions_evictsMembers_andAudits() {
        actorHas(Permission.all());
        TenantRole role = stored(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER));

        RoleResponse updated = service.updateRole(TENANT, ACTOR, role.getId(),
                request("Panel member", List.of("interviews.conduct", "team.view"), 0));

        assertThat(updated.name()).isEqualTo("Panel member");
        assertThat(updated.permissions()).containsExactly("team.view", "interviews.conduct");
        // A default role keeps its identity when renamed.
        assertThat(updated.systemRole()).isEqualTo("INTERVIEWER");
        verify(resolver).evictRole(role.getId());
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getPreviousValue()).isEqualTo("Interviewer: interviews.conduct");
        assertThat(audit.getValue().getNewValue()).isEqualTo("Panel member: team.view, interviews.conduct");
    }

    @Test
    void update_ofOwner_isRefused() {
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));

        assertThatThrownBy(() -> service.updateRole(TENANT, ACTOR, owner.getId(), request("Boss", List.of(), 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The Owner role can't be changed. It always has every permission.");
    }

    @Test
    void update_withAStaleVersion_isAConflict() {
        TenantRole role = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));

        assertThatThrownBy(() -> service.updateRole(TENANT, ACTOR, role.getId(), request("R", List.of(), 3)))
                .isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void update_withoutAVersion_isABadRequest() {
        TenantRole role = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));

        assertThatThrownBy(() -> service.updateRole(TENANT, ACTOR, role.getId(), request("R", List.of(), null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void update_cannotStripAPermissionTheEditorDoesNotHold() {
        // Removing "publish" from a role is as much a use of that power as granting it.
        actorHas(EnumSet.of(Permission.TEAM_VIEW, Permission.ROLES_MANAGE));
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));

        assertThatThrownBy(() -> service.updateRole(TENANT, ACTOR, recruiter.getId(),
                request("Recruiter", List.of(), 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("You can only give out permissions you have yourself.");
    }

    @Test
    void anotherCompanysRole_isNotFound() {
        UUID foreign = UUID.randomUUID();
        when(roleRepository.findByIdAndTenantId(foreign, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRole(TENANT, foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateRole(TENANT, ACTOR, foreign, request("x", List.of(), 0)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteRole(TENANT, ACTOR, foreign, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ delete

    @Test
    void delete_ofADefaultRole_isRefused() {
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));

        assertThatThrownBy(() -> service.deleteRole(TENANT, ACTOR, recruiter.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Default roles can't be deleted. You can rename and edit them instead.");
    }

    @Test
    void delete_ofARoleInUse_needsSomewhereToMoveItsMembers() {
        actorHas(Permission.all());
        TenantRole custom = stored(TenantRole.custom(TENANT, "Coordinator", null, EnumSet.of(Permission.TEAM_VIEW)));
        List<User> members = List.of(member(custom), member(custom));
        when(userRepository.findAllByTenantRoleId(custom.getId())).thenReturn(members);

        assertThatThrownBy(() -> service.deleteRole(TENANT, ACTOR, custom.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("2 people have this role. Choose a role to move them to before deleting it.");
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_movesMembersToTheChosenRole_thenDeletes() {
        actorHas(Permission.all());
        TenantRole custom = stored(TenantRole.custom(TENANT, "Coordinator", null, EnumSet.of(Permission.TEAM_VIEW)));
        TenantRole interviewer = stored(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER));
        User member = member(custom);
        when(userRepository.findAllByTenantRoleId(custom.getId())).thenReturn(List.of(member));

        service.deleteRole(TENANT, ACTOR, custom.getId(), interviewer.getId());

        assertThat(member.getTenantRole()).isSameAs(interviewer);
        // The transitional fixed role follows the move.
        assertThat(member.getRole().getName()).isEqualTo(RoleName.INTERVIEWER);
        verify(roleRepository).delete(custom);
        verify(resolver).evictUser(member.getId());
        verify(resolver).evictRole(custom.getId());
    }

    @Test
    void delete_cannotMoveMembersIntoOwner_unlessTheActorIsAnOwner() {
        actorHas(Permission.all());
        actorIsOwner(false);
        TenantRole custom = stored(TenantRole.custom(TENANT, "Coordinator", null, EnumSet.of(Permission.TEAM_VIEW)));
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));
        List<User> members = List.of(member(custom));
        when(userRepository.findAllByTenantRoleId(custom.getId())).thenReturn(members);

        assertThatThrownBy(() -> service.deleteRole(TENANT, ACTOR, custom.getId(), owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only an Owner can make someone an Owner or change an Owner's role.");
    }

    // ------------------------------------------------------------ member roles

    @Test
    void changeMemberRole_movesTheMember_audits_andEvictsTheirCache() {
        actorHas(Permission.all());
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));
        TenantRole interviewer = stored(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER));
        User member = member(recruiter);

        service.changeMemberRole(TENANT, ACTOR, member.getId(), interviewer.getId());

        assertThat(member.getTenantRole()).isSameAs(interviewer);
        verify(resolver).evictUser(member.getId());
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(RoleAuditAction.MEMBER_ROLE_CHANGED);
        assertThat(audit.getValue().getPreviousValue()).isEqualTo("Recruiter");
        assertThat(audit.getValue().getNewValue()).isEqualTo("Interviewer");
    }

    @Test
    void changeMemberRole_toACustomRole_clearsTheTransitionalFixedRole() {
        actorHas(Permission.all());
        TenantRole interviewer = stored(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER));
        TenantRole custom = stored(TenantRole.custom(TENANT, "Panel", null, EnumSet.of(Permission.INTERVIEWS_CONDUCT)));
        User member = member(interviewer);

        service.changeMemberRole(TENANT, ACTOR, member.getId(), custom.getId());

        assertThat(member.getRole()).isNull();
        assertThat(member.legacyRoleName()).isEqualTo("CUSTOM");
    }

    @Test
    void changeMemberRole_theLastActiveOwner_cannotBeMoved() {
        actorHas(Permission.all());
        actorIsOwner(true);
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));
        User onlyOwner = member(owner);
        when(userRepository.countByTenantIdAndTenantRole_SystemRoleAndStatus(TENANT, SystemRole.OWNER, UserStatus.ACTIVE))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.changeMemberRole(TENANT, ACTOR, onlyOwner.getId(), recruiter.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("Every workspace needs at least one Owner.");
        assertThat(onlyOwner.getTenantRole()).isSameAs(owner);
    }

    @Test
    void changeMemberRole_anOwnerAmongSeveral_canBeMovedByAnOwner() {
        actorHas(Permission.all());
        actorIsOwner(true);
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));
        User coOwner = member(owner);
        when(userRepository.countByTenantIdAndTenantRole_SystemRoleAndStatus(TENANT, SystemRole.OWNER, UserStatus.ACTIVE))
                .thenReturn(2L);

        service.changeMemberRole(TENANT, ACTOR, coOwner.getId(), recruiter.getId());

        assertThat(coOwner.getTenantRole()).isSameAs(recruiter);
    }

    @Test
    void changeMemberRole_onlyAnOwnerCanPromoteToOwner() {
        actorHas(Permission.all());
        actorIsOwner(false);
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));
        User member = member(stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER)));

        assertThatThrownBy(() -> service.changeMemberRole(TENANT, ACTOR, member.getId(), owner.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only an Owner can make someone an Owner or change an Owner's role.");
    }

    @Test
    void changeMemberRole_cannotDemoteSomeoneWhoCanDoMoreThanTheActor() {
        actorHas(EnumSet.of(Permission.TEAM_VIEW, Permission.TEAM_MANAGE, Permission.INTERVIEWS_CONDUCT));
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));
        TenantRole interviewer = stored(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER));
        User strongerMember = member(recruiter);

        assertThatThrownBy(() -> service.changeMemberRole(TENANT, ACTOR, strongerMember.getId(), interviewer.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("You can only give out permissions you have yourself.");
    }

    @Test
    void changeMemberRole_ofSomeoneInAnotherCompany_isNotFound() {
        User outsider = new User(UUID.randomUUID(), TenantRole.fromTemplate(UUID.randomUUID(), SystemRole.INTERVIEWER),
                null, "x@other.io", "h", "X", "Y", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(outsider, "id", UUID.randomUUID());
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.changeMemberRole(TENANT, ACTOR, outsider.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Team member not found");
    }

    // ------------------------------------------------------------- invitations

    @Test
    void roleForInvitation_mapsTheOldRoleNamesToTheDefaultRoles() {
        actorHas(Permission.all());
        TenantRole recruiter = TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER);
        when(roleRepository.findByTenantIdAndSystemRole(TENANT, SystemRole.RECRUITER)).thenReturn(Optional.of(recruiter));

        assertThat(service.roleForInvitation(TENANT, ACTOR, null, "HR_MANAGER")).isSameAs(recruiter);
    }

    @Test
    void roleForInvitation_neverOwner() {
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));

        assertThatThrownBy(() -> service.roleForInvitation(TENANT, ACTOR, owner.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("People can't be invited as Owner.");
    }

    @Test
    void roleForInvitation_neverMoreThanTheInviterHolds() {
        actorHas(EnumSet.of(Permission.TEAM_VIEW, Permission.TEAM_INVITE));
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));

        assertThatThrownBy(() -> service.roleForInvitation(TENANT, ACTOR, recruiter.getId(), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void roleForInvitation_needsARole() {
        assertThatThrownBy(() -> service.roleForInvitation(TENANT, ACTOR, null, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose a role for the new member.");
    }

    // ------------------------------------------------------------------ listing

    @Test
    void listRoles_showsDefaultRolesFirstThenCustomByName_withMemberCounts() {
        TenantRole owner = stored(TenantRole.fromTemplate(TENANT, SystemRole.OWNER));
        TenantRole interviewer = stored(TenantRole.fromTemplate(TENANT, SystemRole.INTERVIEWER));
        TenantRole recruiter = stored(TenantRole.fromTemplate(TENANT, SystemRole.RECRUITER));
        TenantRole zebra = stored(TenantRole.custom(TENANT, "Zebra", null, Set.of()));
        TenantRole alpha = stored(TenantRole.custom(TENANT, "alpha", null, Set.of()));
        when(roleRepository.findAllByTenantId(TENANT)).thenReturn(List.of(zebra, interviewer, alpha, owner, recruiter));
        when(userRepository.countMembersByRole(TENANT)).thenReturn(List.<Object[]>of(new Object[] {owner.getId(), 2L}));

        List<RoleResponse> roles = service.listRoles(TENANT);

        assertThat(roles).extracting(RoleResponse::name)
                .containsExactly("Owner", "Recruiter", "Interviewer", "alpha", "Zebra");
        assertThat(roles.get(0).memberCount()).isEqualTo(2);
        assertThat(roles.get(0).locked()).isTrue();
        assertThat(roles.get(0).permissions()).hasSize(Permission.values().length);
        assertThat(roles.get(1).memberCount()).isZero();
    }

    @Test
    void provisionDefaultRoles_createsOneOfEachTemplate() {
        when(roleRepository.save(any(TenantRole.class))).thenAnswer(call -> call.getArgument(0));

        var roles = service.provisionDefaultRoles(TENANT);

        assertThat(roles).containsOnlyKeys(SystemRole.values());
        assertThat(roles.get(SystemRole.OWNER).isOwner()).isTrue();
    }

    // ----------------------------------------------------------------- helpers

    private void actorHas(Set<Permission> permissions) {
        lenient().when(resolver.currentPermissions(ACTOR)).thenReturn(permissions);
    }

    private void actorIsOwner(boolean owner) {
        User actor = new User(TENANT, TenantRole.fromTemplate(TENANT, owner ? SystemRole.OWNER : SystemRole.RECRUITER),
                null, "actor@acme.io", "h", "A", "B", UserStatus.ACTIVE);
        lenient().when(userRepository.findById(ACTOR)).thenReturn(Optional.of(actor));
    }

    private TenantRole stored(TenantRole role) {
        persisted(role);
        lenient().when(roleRepository.findByIdAndTenantId(role.getId(), TENANT)).thenReturn(Optional.of(role));
        return role;
    }

    private static TenantRole persisted(TenantRole role) {
        if (role.getId() == null) {
            ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        }
        return role;
    }

    private User member(TenantRole role) {
        User user = new User(TENANT, role, null, UUID.randomUUID() + "@acme.io", "h", "M", "N", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private static RoleRequest request(String name, List<String> permissions, Integer version) {
        return new RoleRequest(name, null, permissions, version);
    }
}
