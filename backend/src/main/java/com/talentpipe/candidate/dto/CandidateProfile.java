package com.talentpipe.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The candidate module's outward-facing view of a candidate.
 *
 * <p>This is the ONLY candidate representation other modules receive — the
 * {@code Candidate} entity and its password hash never leave the module. The
 * name is pre-split here so every consumer renders it identically.</p>
 *
 * @param id        candidate id (also the {@code sub} claim of their token)
 * @param email     globally unique — candidates are tenant-independent
 * @param firstName leading part of the full name
 * @param lastName  trailing part, empty when the name is a single word
 * @param status    lifecycle state name (ACTIVE, PENDING_VERIFICATION, DISABLED)
 * @param createdAt registration timestamp
 */
public record CandidateProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String status,
        Instant createdAt) {
}
