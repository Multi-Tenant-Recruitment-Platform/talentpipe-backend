package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Invitation request (PB-003 / PB-004).
 *
 * <p>Carries no tenant: the invitee always joins the caller's tenant, taken
 * from the caller's token. A tenant in the body would let an admin plant users
 * in someone else's company.</p>
 *
 * <p>The role to join as is given by {@code roleId} — any of the workspace's
 * roles except Owner (people join as something else and can be made Owner
 * once they have accepted). {@code role} is the old way of saying it,
 * {@code HR_MANAGER} or {@code INTERVIEWER}, still accepted so existing
 * clients keep working; it maps to the Recruiter and Interviewer roles. Send
 * one or the other.</p>
 */
public record InviteUserRequest(

        @NotBlank(message = "Enter their first name.")
        @Size(max = 100, message = "Keep the first name to 100 characters or fewer.")
        String firstName,

        @NotBlank(message = "Enter their last name.")
        @Size(max = 100, message = "Keep the last name to 100 characters or fewer.")
        String lastName,

        @NotBlank(message = "Enter their email address.")
        @Email(message = "Enter a valid email address, like name@company.com.")
        @Size(max = 255, message = "Keep the email address to 255 characters or fewer.")
        String email,

        /** Deprecated: use {@code roleId}. */
        @Pattern(regexp = "HR_MANAGER|INTERVIEWER", message = "role must be HR_MANAGER or INTERVIEWER.")
        String role,

        UUID roleId
) {
}
