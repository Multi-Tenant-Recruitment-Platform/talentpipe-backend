package com.talentpipe.candidate.dto;

import java.util.UUID;

/**
 * Full candidate profile response returned to the authenticated candidate.
 *
 * @param id                 candidate UUID
 * @param fullName           candidate's legal full name
 * @param email              candidate's contact email
 * @param phone              contact phone number, or null
 * @param identityCardNumber NIC or passport number
 * @param photoUrl           publicly accessible avatar URL, or null
 * @param inTalentPool       whether the candidate is currently opted into the Talent Pool
 * @param cv                 active CV metadata, or null
 */
public record MyCandidateProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String identityCardNumber,
        String photoUrl,
        boolean inTalentPool,
        CandidateCvResponse cv
) {
}
