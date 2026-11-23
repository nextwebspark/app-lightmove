package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** One pair a person labels: a fictional case and two versions' answers to it, as A and B. */
public record CalibrationPair(UUID id, int position, UUID caseId, String caseKey, String caseName,
                              EvalCaseSplit split, JsonNode input, JsonNode expected, JsonNode answerA,
                              JsonNode answerB) {}
