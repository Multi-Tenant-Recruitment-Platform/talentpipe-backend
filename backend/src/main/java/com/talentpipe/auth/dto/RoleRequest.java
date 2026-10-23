package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/v1/roles} and {@code PUT /api/v1/roles/{id}}: a
 * role the company defines — its name, what it is for, and the permissions
 * ("tasks") it grants.
 *
 * <p>{@code permissions} are keys from {@code GET /api/v1/permissions}. A
 * permission that another one depends on is added automatically, and the
 * response shows the set as stored.</p>
 *
 * @param version the {@code version} of the role being edited; required on
 *                {@code PUT} so a save based on a stale copy answers 409, and
 *                ignored on {@code POST}
 */
public record RoleRequest(

        @NotBlank(message = "Give the role a name.")
        @Size(max = 60, message = "Keep the role name to 60 characters or fewer.")
        String name,

        @Size(max = 300, message = "Keep the description to 300 characters or fewer.")
        String description,

        @NotNull(message = "Choose what this role can do.")
        @Size(max = 100, message = "That is more permissions than exist.")
        List<String> permissions,

        @PositiveOrZero(message = "The role's version is not valid. Reload the page and try again.")
        Integer version
) {
}
