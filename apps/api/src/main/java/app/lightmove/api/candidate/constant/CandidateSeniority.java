package app.lightmove.api.candidate.constant;

/**
 * How far an executive sits from the chief executive, which is the axis a search brief is written in:
 * a mandate asks for an N-1 in a market, not for a job title, because the same title means different
 * things in a family holding and in a listed multinational.
 *
 * <p>Nullable on the row. A researcher who has a name and a title has not necessarily worked out the
 * reporting line yet, and guessing one would put a fabricated fact in front of a client.
 *
 * <p>The stored name and the wire token differ on purpose. {@code N-1} is what a consultant writes and
 * what the UI shows, and it is not a legal Java identifier; the schema stores {@code N_MINUS_1} so the
 * CHECK constraint and the enum agree, exactly as the triage enums do.
 */
public enum CandidateSeniority {

    /** The chief executive of the business or the region. */
    N("N"),

    /** Reports to N — the functional heads a mandate most often targets. */
    N_MINUS_1("N-1"),

    N_MINUS_2("N-2"),

    N_MINUS_3("N-3");

    private final String wireToken;

    CandidateSeniority(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its level, or {@code null} if unknown. */
    public static CandidateSeniority fromValue(String value) {
        for (CandidateSeniority level : values()) {
            if (level.wireToken.equals(value)) {
                return level;
            }
        }
        return null;
    }
}
