package app.lightmove.api.report.dto;

/**
 * One competency of the brief, in the brief's order.
 *
 * @param key    the competency's match key, opaque to the screen: what a scenario's weights and a score are keyed on
 * @param panel  {@code technical} or {@code behavioural}
 * @param weight the brief's own weight — the agreed baseline
 */
public record RankingCriterionDto(String key, String panel, String name, String description, int weight) {}
