package app.lightmove.api.assistant.tool;

/**
 * What the model is told about the proposal it just made.
 *
 * <p>{@code dropped} is not a formality. The tool resolves, caps and filters what the model asked
 * for, so the card can carry fewer companies than the model named — and a model that then writes
 * "here are the ten I found" beside a card showing six has misled the person reading both. This is
 * the same instinct as {@code CompanyMatches.matched}: tell the model what it is not looking at.
 */
public record ProposalPlaced(int proposed, int dropped) {
}
