package app.lightmove.api.common.industry.model;

/**
 * One industry in every form a row stores it: the label the company universe publishes, LinkedIn's
 * id for it, LinkedIn V2's name for the same id, and the sector a consultant ticks.
 *
 * <p>They travel together because a row carrying all four has to get them from one lookup. Two
 * writers reading the same map at different moments is how a row starts disagreeing with itself,
 * which is the failure the vocabulary was introduced to end.
 *
 * <p><b>{@code v2Label} is not finer data on an Apollo-sourced company.</b> The universe only ever
 * recorded the coarse label, so this is V1 renamed. A vendor-captured company states its own leaf,
 * and the two are not the same strength of claim.
 *
 * <p>Every field but {@code label} is null for an industry nobody could resolve — that is a
 * different fact from any sector it might otherwise have been filed under.
 */
public record ResolvedIndustry(String label, Integer linkedInCode, String v2Label, String sectorGroup) {
}
