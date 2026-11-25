package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.FinishKind;
import tools.jackson.databind.JsonNode;

/**
 * What one Try it answered: the model's own text, the same answer as the feature bound it (null when it bound
 * nothing), and what the call cost. Holds a fictional input's answer only.
 *
 * @param modelCalls how often the model answered, a repair's second ask among them; 0 when it never did
 */
public record PromptTrialResult(
        String rawAnswer,
        JsonNode parsedAnswer,
        String model,
        long inputTokens,
        long outputTokens,
        FinishKind finish,
        long tookMillis,
        int modelCalls
) {
}
