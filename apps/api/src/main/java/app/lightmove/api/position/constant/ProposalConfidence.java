package app.lightmove.api.position.constant;

/** How far a proposed field is worth trusting before it is accepted into the brief. */
public enum ProposalConfidence {

    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String wireToken;

    ProposalConfidence(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }
}
