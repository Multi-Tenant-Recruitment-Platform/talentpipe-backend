package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import java.util.UUID;
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
}
