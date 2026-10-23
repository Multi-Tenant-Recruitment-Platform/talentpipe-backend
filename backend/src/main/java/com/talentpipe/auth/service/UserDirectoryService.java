package com.talentpipe.auth.service;

import com.talentpipe.auth.dto.MemberSummary;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only questions other modules may ask about workspace members, without
 * ever seeing a {@code User} entity or the repository behind it.
 *
 * <p>Exists because modules outside auth sometimes hold a user id and need to
 * know whether it is one they may use — the job module, for instance, before
 * it assigns a recruiter to a vacancy. Answering here keeps the tenant-scope
 * check next to the data it protects instead of re-implemented by each caller.</p>
 */
@Service
public class UserDirectoryService {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Whether {@code userId} is an ACTIVE member of {@code tenantId}.
     *
     * <p>False for a user in another tenant, exactly as for a user that does
     * not exist: the caller learns only "not yours to use", never whether the
     * id is real somewhere else. Invited-but-not-yet-accepted, unverified and
     * disabled accounts are not active members.</p>
     */
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .filter(user -> tenantId.equals(user.getTenantId()))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
    }

    /**
     * Which of {@code userIds} are ACTIVE members of {@code tenantId} — the
     * bulk form of {@link #isActiveMember}, for checking a whole list at once.
     */
    @Transactional(readOnly = true)
    public Set<UUID> activeMembersAmong(UUID tenantId, Collection<UUID> userIds) {
        if (tenantId == null || userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return userRepository.findAllByTenantIdAndIdIn(tenantId, userIds).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Name, email, role and status of the given members, keyed by id, for a
     * screen that shows who works on something. Ids outside the tenant are
     * simply absent, as are ones that no longer exist.
     */
    @Transactional(readOnly = true)
    public Map<UUID, MemberSummary> describe(UUID tenantId, Collection<UUID> userIds) {
        if (tenantId == null || userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByTenantIdAndIdIn(tenantId, userIds).stream()
                .map(UserDirectoryService::toSummary)
                .collect(Collectors.toUnmodifiableMap(MemberSummary::id, Function.identity()));
    }

    private static MemberSummary toSummary(User user) {
        return new MemberSummary(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getTenantRole() == null ? null : user.getTenantRole().getName(),
                user.getStatus().name());
    }
}
