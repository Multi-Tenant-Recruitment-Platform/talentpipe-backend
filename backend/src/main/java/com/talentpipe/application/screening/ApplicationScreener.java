package com.talentpipe.application.screening;

import com.talentpipe.application.entity.ScreeningResult;

/**
 * Scores an application against its vacancy.
 *
 * <p>Which one runs is {@code talentpipe.ai.screening-mode}: {@code remote}
 * (the default) asks the AI service; {@code stub} is a deterministic stand-in
 * for local development and demos; {@code off} provides none, and
 * applications wait unscreened.</p>
 */
public interface ApplicationScreener {

    /**
     * @throws ScreeningUnavailableException when no score can be produced now;
     *         its message is shown to recruiters, so it must not quote the CV
     */
    ScreeningResult screen(ScreeningInput input);
}
