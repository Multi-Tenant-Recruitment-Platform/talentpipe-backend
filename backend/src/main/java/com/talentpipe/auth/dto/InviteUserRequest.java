package com.talentpipe.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Invitation request from a COMPANY_ADMIN (PB-003 / PB-004).
 *
 * <p>Carries no tenant: the invitee always joins the caller's tenant, taken
 * from the caller's token. A tenant in the body would let an admin plant users
 * in someone else's company.</p>
 *
 * <p>{@code role} is restricted to the two invitable roles — COMPANY_ADMIN and
 * SUPER_ADMIN are deliberately not grantable through this endpoint, and
 * CANDIDATE is a different identity entirely.</p>
 */
public record InviteUserRequest(

        @NotBlank(message = "firstName is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String email,

        @NotNull(message = "role is required")
        @Pattern(regexp = "HR_MANAGER|INTERVIEWER",
                message = "must be HR_MANAGER or INTERVIEWER")
        String role
) {
}
