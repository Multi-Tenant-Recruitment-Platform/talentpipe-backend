package com.talentpipe.auth.dto;

/**
 * Returned when an invitation is accepted (PB-003 / PB-004).
 *
 * <p>The invitee lands on the login page next, which needs the workspace
 * subdomain to sign in — but they never chose it and were never told it, and
 * a fresh device opened from the email link has nothing remembered. Sending
 * it here lets the SPA prefill the form instead of asking for the one thing
 * the invitation flow was supposed to make unnecessary. The email prefills
 * the second field for the same reason.</p>
 */
public record AcceptInviteResponse(
        String email,
        String subdomain
) {
}
