package app.lightmove.api.prompteval.model;

import app.lightmove.api.core.llm.constant.EvalCheckVerdict;
import app.lightmove.api.prompteval.constant.EvalRunSide;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * What one version answered to one case on one repeat, and each check's verdict on it by check id. {@code answer}
 * is null when the model answered nothing the feature could read.
 */
public record EvalRunAnswer(UUID caseId, EvalRunSide side, int repeat, JsonNode answer,
                            Map<String, EvalCheckVerdict> verdicts) {}
