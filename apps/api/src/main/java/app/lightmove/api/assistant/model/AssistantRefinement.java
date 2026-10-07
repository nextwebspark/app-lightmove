package app.lightmove.api.assistant.model;

import app.lightmove.api.assistant.constant.RefinementKind;

/**
 * One next search the panel offers under an answer. {@code prompt} restates the whole search, so asking
 * it leaves the model nothing to infer; {@code projected} is the counted universe it would leave.
 */
public record AssistantRefinement(RefinementKind kind, String label, String prompt, long projected) {}
