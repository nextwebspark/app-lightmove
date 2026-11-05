package app.lightmove.api.enrichment.sourcing.constant;

/** How strongly someone who fits the role fits it, read off their score: 75 and up, 55 to 74, below 55. */
public enum SuggestionBand {
    STRONG,
    GOOD,
    POSSIBLE;

    public static SuggestionBand of(int score) {
        return score >= 75 ? STRONG : score >= 55 ? GOOD : POSSIBLE;
    }
}
