package com.talentpipe.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * The {@code details} of a {@code WORKSPACE_SELECTION_REQUIRED} error: the
 * accounts the email and password fit, and the token that exchanges one of
 * them for a session at {@code POST /api/v1/auth/login/select}.
 *
 * @param selectionToken   single use; send it back with the chosen {@code accountId}
 * @param expiresInSeconds how long the token stays valid
 * @param accounts         company workspaces by name, then the platform
 *                         account, then the candidate account
 */
public record WorkspaceSelectionResponse(
        String selectionToken,
        long expiresInSeconds,
        List<Choice> accounts
) {

    /** What kind of account a choice signs in to. */
    public enum AccountType {
        /** A member of a company workspace. */
        COMPANY,
        /** A platform administrator; belongs to no workspace. */
        PLATFORM,
        /** A job seeker. */
        CANDIDATE
    }

    /**
     * One account to offer.
     *
     * @param workspaceName company name; absent for platform and candidate accounts
     * @param subdomain     company subdomain; absent for platform and candidate accounts
     * @param logoUrl       company logo; absent when there is none
     * @param roleName      the person's role there, for display ("Recruiter", "Candidate", …)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Choice(
            UUID accountId,
            AccountType type,
            String workspaceName,
            String subdomain,
            String logoUrl,
            String roleName
    ) {
    }
}
