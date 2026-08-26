package app.lightmove.api.strategy.dto;

/**
 * A selectable value the sidebar offers without counting: the token a filter stores, beside the chip's
 * label.
 *
 * <p>Location is the one axis shaped this way. Six GCC countries are a set whose <i>shape</i> is the
 * information, and a number on each pill was noise on the one accordion that reads as chips rather
 * than a list — so the count is neither shown nor computed. The order still is: {@code countryFacets}
 * ranks by size in SQL, so the largest market is the first chip while the figure behind that never
 * leaves the database.
 *
 * <p>Value and label hold the same string for a country today, and the pair is kept rather than
 * collapsed to one so that retitling "United Arab Emirates" to "UAE" stays presentation — the rule
 * {@link FacetCount} follows for every axis that does carry a number.
 */
public record FacetValue(String value, String label) {}
