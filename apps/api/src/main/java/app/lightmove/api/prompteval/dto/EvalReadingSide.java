package app.lightmove.api.prompteval.dto;

/**
 * What one version said about one scored thing across its repeats: the mean score and its standard deviation,
 * {@code outside} when most of its answers scored outside what the case allows, and the first answer's own words.
 * {@code mean} is null when no answer scored it, {@code spread} with fewer than two that did.
 */
public record EvalReadingSide(Double mean, Double spread, boolean outside, String text) {
}
