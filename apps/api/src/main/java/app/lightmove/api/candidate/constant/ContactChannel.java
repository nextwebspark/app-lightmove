package app.lightmove.api.candidate.constant;

/**
 * Which contact a value is — and which a lookup is after, since the two bill from separate pools and
 * are asked for separately. Stored by name, matching V54's CHECK.
 */
public enum ContactChannel {

    EMAIL("email"),
    PHONE("phone");

    private final String value;

    ContactChannel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
