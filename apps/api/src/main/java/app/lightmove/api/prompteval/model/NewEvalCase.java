package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import tools.jackson.databind.JsonNode;

/** A case about to be added to a dataset. {@code expected} is null for a prompt only a judge can score. */
public record NewEvalCase(String key, String name, String tag, EvalCaseSplit split, JsonNode input,
                          JsonNode expected) {}
