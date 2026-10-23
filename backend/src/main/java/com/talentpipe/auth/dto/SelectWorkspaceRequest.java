package com.talentpipe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Second step of a sign-in whose credentials matched several accounts: which
 * of the offered accounts to sign in to.
 *
 * @param selectionToken the token from the {@code WORKSPACE_SELECTION_REQUIRED} response
 * @param accountId      one of the {@code accountId}s offered in that response
 */
public record SelectWorkspaceRequest(

        @NotBlank(message = "Sign in again to choose a workspace.")
        String selectionToken,

        @NotNull(message = "Choose the account you want to sign in to.")
        UUID accountId
) {
}
