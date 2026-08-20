package app.lightmove.api.triagecompany.constant;

/**
 * Where a company stands in a mandate's triage. A company enters the universe from the Strategy
 * screen and moves between these as the team triages it; there is no "untriaged" state, because a
 * company nobody has taken a position on simply has no row.
 */
public enum TriageCompanyStatus {

    /** Taken into the mandate's working set. What "Add to Universe" writes. */
    IN_UNIVERSE("inUniverse"),

    /** Promoted: worth mapping people at. */
    SHORTLISTED("shortlisted"),

    /**
     * Ruled out. Kept rather than deleted, so re-running "Add all to Universe" after widening the
     * filter cannot quietly resurrect a company the team already decided against.
     */
    DECLINED("declined");

    private final String wireToken;

    TriageCompanyStatus(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The wire value; the frontend's status tabs carry the same tokens. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its status, or {@code null} if unknown. */
    public static TriageCompanyStatus fromValue(String value) {
        for (TriageCompanyStatus status : values()) {
            if (status.wireToken.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
