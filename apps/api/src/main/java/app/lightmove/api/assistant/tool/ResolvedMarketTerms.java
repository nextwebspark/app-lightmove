package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * A search's country and industry as the universe spells them. A region or a sector reads as several
 * values, and {@code interpretedAs} says so; {@code unrecognised} is what could not be read at all.
 */
record ResolvedMarketTerms(List<String> countries, String countryLabel, List<String> industries,
                           String industryLabel, List<String> interpretedAs, List<String> unrecognised) {

    boolean isFullyRecognised() {
        return unrecognised.isEmpty();
    }
}
