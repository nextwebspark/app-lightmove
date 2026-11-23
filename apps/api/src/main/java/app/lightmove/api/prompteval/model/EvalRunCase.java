package app.lightmove.api.prompteval.model;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** A case as a run answers and scores it. {@code expected} is null for a case with no label. */
public record EvalRunCase(UUID id, String key, String name, String tag, JsonNode input, JsonNode expected) {}
