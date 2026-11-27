package com.talentpipe.application.screening;

import com.talentpipe.application.entity.CriterionScore;
import com.talentpipe.application.entity.ScreeningResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * A deterministic stand-in for the AI service, for local development and
 * demos only ({@code talentpipe.ai.screening-mode=stub}).
 *
 * <p><strong>Not an assessment.</strong> With CV text it scores the share of
 * the vacancy's (weighted) skills the text mentions. Without any — the demo
 * candidates have no CVs — it derives a stable score from the application's
 * id, so a demo looks the same every time. Every result says so in its
 * summary and is recorded with {@link #MODEL_VERSION}, and the application
 * refuses to start with it under a production profile.</p>
 */
@Component
@ConditionalOnProperty(name = "talentpipe.ai.screening-mode", havingValue = "stub")
public class StubApplicationScreener implements ApplicationScreener {

    public static final String MODEL_VERSION = "stub-v1";

    private static final Logger log = LoggerFactory.getLogger(StubApplicationScreener.class);
    private static final int MATCHED = 90;
    private static final int MISSING = 20;
    private static final int MAX_LISTED = 3;

    public StubApplicationScreener(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "talentpipe.ai.screening-mode=stub produces made-up scores and must not run in production");
        }
        log.warn("Screening with the STUB scorer: scores are for development and demos, not assessments.");
    }

    @Override
    public ScreeningResult screen(ScreeningInput input) {
        List<ScreeningInput.Criterion> criteria = input.criteria();
        if (criteria.isEmpty()) {
            throw new ScreeningUnavailableException("This vacancy lists no skills to screen against.");
        }
        boolean fromCv = input.hasCvText();
        String text = fromCv ? input.cvText().toLowerCase(Locale.ROOT) : null;
        // Demo mode: a stable share of the skills, between roughly a third and all of them.
        long seed = input.applicationId().getMostSignificantBits() ^ input.applicationId().getLeastSignificantBits();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<CriterionScore> scores = new ArrayList<>();
        double total = 0;
        double earned = 0;
        for (int i = 0; i < criteria.size(); i++) {
            ScreeningInput.Criterion criterion = criteria.get(i);
            boolean shows = fromCv ? mentions(text, criterion.name()) : demoHit(seed, i);
            total += criterion.weight();
            if (shows) {
                earned += criterion.weight();
                matched.add(criterion.name());
            } else {
                missing.add(criterion.name());
            }
            scores.add(new CriterionScore(criterion.name(), shows ? MATCHED : MISSING,
                    rationale(shows, fromCv)));
        }
        int overall = total <= 0 ? 0 : (int) Math.round(100 * earned / total);

        String summary = (fromCv
                ? "Stub score from skill mentions in the CV — not an AI assessment."
                : "Demo score: there is no CV text, so this is generated for demonstration and is not an assessment.")
                + " Matches " + matched.size() + " of " + criteria.size() + " skills.";
        return new ScreeningResult(overall, summary,
                matched.stream().limit(MAX_LISTED).map(skill -> "Shows " + skill).toList(),
                missing.stream().limit(MAX_LISTED).map(skill -> "No evidence of " + skill).toList(),
                scores, MODEL_VERSION, fromCv ? "cv-keywords" : "demo-seed", false, matched, missing);
    }

    private static boolean mentions(String text, String skill) {
        String needle = skill.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            return false;
        }
        // Whole words, so "Java" does not match inside "JavaScript".
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])")
                .matcher(text).find();
    }

    /** About two in three skills hit, chosen by the application's id so the demo is repeatable. */
    private static boolean demoHit(long seed, int index) {
        long mixed = seed * 0x9E3779B97F4A7C15L + index * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 31;
        return Math.floorMod(mixed, 3) != 0;
    }

    private static String rationale(boolean shows, boolean fromCv) {
        if (!fromCv) {
            return "Demo value — no CV text was available.";
        }
        return shows ? "Mentioned in the CV." : "Not mentioned in the CV.";
    }
}
