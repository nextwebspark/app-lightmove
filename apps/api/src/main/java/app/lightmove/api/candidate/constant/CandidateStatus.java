package app.lightmove.api.candidate.constant;

/**
 * Where an executive stands in a mandate's research, from "we know this person exists" to the three
 * ways they leave the running. Deliberately not a shortlist flag: a person ruled out is kept with the
 * reason rather than deleted, because the same name will come up on the next mandate.
 *
 * <p>{@link #OFF_LIMITS} is about the person and is not the same thing as a mandate's off-limits
 * <i>companies</i>, which live on the strategy and bar a company from the search entirely.
 */
public enum CandidateStatus {

    IDENTIFIED("identified"),

    CONTACTED("contacted"),

    ENGAGED("engaged"),

    INTERESTED("interested"),

    NOT_INTERESTED("notInterested"),

    /** Cannot be approached — an off-limits agreement, or a prior placement. */
    OFF_LIMITS("offLimits"),

    OUT_OF_SCOPE("outOfScope");

    private final String wireToken;

    CandidateStatus(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    public static CandidateStatus fromValue(String value) {
        for (CandidateStatus status : values()) {
            if (status.wireToken.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
