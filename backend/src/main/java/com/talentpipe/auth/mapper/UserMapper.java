package com.talentpipe.auth.mapper;

import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.entity.User;
import org.springframework.stereotype.Component;

/**
 * Entity → DTO mapping for the auth module. The password hash is dropped here
 * by construction: {@link UserResponse} has no field that could carry it.
 */
@Component
public class UserMapper {

    /**
     * @param tenantName display name of the user's tenant, resolved by the
     *                   caller via {@code TenantService} ({@code null} for
     *                   SUPER_ADMIN)
     */
    public UserResponse toResponse(User user, String tenantName) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                tenantName,
                user.getRole().getName().name(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.getCreatedAt());
    }
}
