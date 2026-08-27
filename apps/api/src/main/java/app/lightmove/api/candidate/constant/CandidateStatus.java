package app.lightmove.api.candidate.constant;

/**
 * Where an executive stands in a mandate's research. The whole line, from "we know this person exists"
 * to the three ways they leave the running.
 *
 * <p>This is deliberately not a shortlist flag. Talent mapping records the market as it is, so a
 * person ruled out is kept with the reason rather than deleted — the map is worth less if it only
 * shows the people still in play, and the same name will come up on the next mandate.
 *
 * <p>{@link #OFF_LIMITS} is about the person and is not the same thing as a mandate's off-limits
 * <i>companies</i>, which live on the strategy and bar a company from the search entirely.
 */
public enum CandidateStatus {

    /** Mapped and nothing more: a name in a seat. Where every profile starts. */
    IDENTIFIED("identified"),

    /** An approach has gone out. Says nothing about whether it landed. */
    CONTACTED("contacted"),

    /** Talking to us, without having said yes to the role. */
    ENGAGED("engaged"),

    /** Wants to be considered. */
    INTERESTED("interested"),

    NOT_INTERESTED("notInterested"),

    /** Cannot be approached — an off-limits agreement, or a prior placement. */
    OFF_LIMITS("offLimits"),

    /** Approachable and mapped, but not a fit for this brief. */
    OUT_OF_SCOPE("outOfScope");

    private final String wireToken;

    CandidateStatus(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The wire value; the grid's status pills carry the same tokens. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its status, or {@code null} if unknown. */
    public static CandidateStatus fromValue(String value) {
        for (CandidateStatus status : values()) {
            if (status.wireToken.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
