package app.lightmove.api.candidate.constant;

/**
 * Which door a profile came through, and provenance the reader is entitled to see: a compensation
 * figure a researcher took from the executive themselves and one a bulk CSV asserted are not equally
 * trustworthy.
 *
 * <p>All three are reachable. {@link #CSV} is self-asserted rather than proven: it travels on the wire
 * like the others and the import is simply the only thing that sends it, so it is a label on a row and
 * never evidence a reader may lean on.
 */
public enum CandidateSource {

    /** Typed in on the Companies screen. */
    MANUAL("manual"),

    /** Bulk-imported from a spreadsheet a consultant uploaded. */
    CSV("csv"),

    /** Read off a live profile page by the browser plugin, and the capture enrichment researches. */
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
