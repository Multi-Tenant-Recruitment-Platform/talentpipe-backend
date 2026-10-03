package com.talentpipe.job.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.talentpipe.job.entity.ShiftType;
import com.talentpipe.job.entity.WeekDay;
import java.math.BigDecimal;
import java.util.List;

/**
 * The full public advert ({@code GET /api/v1/public/jobs/{slug}}): the board
 * card plus the rest of what the company wrote.
 *
 * <p>On the wire this is one flat object — every {@link JobSummaryResponse}
 * field followed by the fields below. {@code @JsonUnwrapped} produces that
 * shape without repeating the twenty card fields here, so the card and the
 * detail page can never drift apart. (It applies to serialization only, which
 * is all a response needs.)</p>
 *
 * <p>Like the card, it omits everything internal: assignees, pipeline and
 * screening questions are for the hiring team, not the applicant.</p>
 */
public record JobDetailResponse(
        @JsonUnwrapped JobSummaryResponse summary,
        String jobDescription,
        List<String> keyResponsibilities,
        List<String> preferredSkills,
        String education,
        List<String> certifications,
        List<String> languageRequirements,
        String otherRequirements,
        List<String> benefits,
        List<WeekDay> workingDays,
        String workingHours,
        ShiftType shiftType,
        BigDecimal expectedHoursPerWeek
) {
}
