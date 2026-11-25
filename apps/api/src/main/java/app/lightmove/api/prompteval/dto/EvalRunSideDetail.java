package app.lightmove.api.prompteval.dto;

/**
 * One of a run's two versions. {@code modelId} is the model that answered, or the one the version would be answered
 * by until it has; {@code live}: the version serving now.
 */
public record EvalRunSideDetail(int version, boolean live, String modelId, Double temperature) {
}
