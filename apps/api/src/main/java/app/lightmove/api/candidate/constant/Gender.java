package app.lightmove.api.candidate.constant;

/**
 * An executive's gender as a researcher recorded it, for the report's diversity chapter.
 *
 * <p><b>Recorded, never inferred.</b> Nothing derives this from a name, a photo or a pronoun. The
 * chapter that reads it states what somebody entered, and a mandate where nobody entered anything is
 * reported as unmeasured rather than as a pool of one gender.
 *
 * <p>Absent is not {@link #OTHER}: a null column means nobody said, {@code OTHER} means somebody
 * did. The two are counted separately for that reason.
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
