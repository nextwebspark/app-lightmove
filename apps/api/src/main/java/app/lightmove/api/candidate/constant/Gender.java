package app.lightmove.api.candidate.constant;

/**
 * An executive's gender, for the report's diversity chapter.
 *
 * <p>A captured executive's profile can propose this value (issue #458, {@code CandidateBackgroundProposer}
 * reading their name, title, location, about text and career for a signal) — a deliberate change from
 * this enum's original "recorded, never inferred" rule. What the rule protected still holds by another means:
 * {@code Candidate.aiInferredFields} flags a proposed value until a researcher's own edit changes it,
 * so nothing this class holds is presented as a fact somebody entered until somebody has looked at it.
 *
 * <p>Absent is not {@link #OTHER}: a null column means nobody said or confirmed one, {@code OTHER}
 * means somebody did. The two are counted separately for that reason.
 */
public enum Gender {

    FEMALE("female"),

    MALE("male"),

    OTHER("other");

    private final String wireToken;

    Gender(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    public static Gender fromValue(String value) {
        for (Gender gender : values()) {
            if (gender.wireToken.equals(value)) {
                return gender;
            }
        }
        return null;
    }
}
