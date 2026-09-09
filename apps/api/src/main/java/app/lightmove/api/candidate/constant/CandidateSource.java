package app.lightmove.api.candidate.constant;

/**
 * Which door a profile came through — provenance the reader is entitled to see, since a compensation
 * figure taken from the executive and one a bulk CSV asserted are not equally trustworthy.
 *
 * <p>{@link #CSV} is self-asserted rather than proven: it travels on the wire like the others and the
 * import is simply the only thing that sends it, so it is a label and never evidence.
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

    public static CandidateSource fromValue(String value) {
        for (CandidateSource source : values()) {
            if (source.wireToken.equals(value)) {
                return source;
            }
        }
        return null;
    }
}
