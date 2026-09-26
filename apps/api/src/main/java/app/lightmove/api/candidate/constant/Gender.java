package app.lightmove.api.candidate.constant;

/**
 * An executive's gender, for the report's diversity chapter.
 *
 * <p>Recorded by a researcher, or proposed from a researched profile by {@code CandidateBackgroundProposer}
 * and flagged in {@code Candidate.aiInferredFields} until a researcher's edit changes it.
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
