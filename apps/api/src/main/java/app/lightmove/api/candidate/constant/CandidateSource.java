package app.lightmove.api.candidate.constant;

/**
 * Which door a profile came through, and provenance the reader is entitled to see: a compensation
 * figure a researcher took from the executive themselves and one a bulk CSV asserted are not equally
 * trustworthy.
 *
 * <p>Only {@link #MANUAL} is reachable today — the CSV import and the browser plugin's profile capture
 * are not built. The enum carries all three from the start anyway, because the alternative is
 * backfilling a provenance nobody recorded.
 */
public enum CandidateSource {

    /** Typed in on the Companies screen. */
    MANUAL("manual"),

    /** Bulk-imported from a spreadsheet of research. Not yet built. */
    CSV("csv"),

    /** Read off a live profile page by the browser plugin. Not yet built. */
    EXTENSION("extension");

    private final String wireToken;

    CandidateSource(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its source, or {@code null} if unknown. */
    public static CandidateSource fromValue(String value) {
        for (CandidateSource source : values()) {
            if (source.wireToken.equals(value)) {
                return source;
            }
        }
        return null;
    }
}
