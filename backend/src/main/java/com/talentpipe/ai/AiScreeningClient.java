package com.talentpipe.ai;

import com.talentpipe.ai.dto.AiScreeningRequest;
import com.talentpipe.ai.dto.AiScreeningResponse;

/**
 * Port interface isolating CV screening evaluation behind an abstraction (DIP).
 */
public interface AiScreeningClient {

    AiScreeningResponse screen(AiScreeningRequest request);
}
