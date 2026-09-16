package app.lightmove.api.enrichment.contact.constant;

/** Which contact a lookup is after. The two bill from separate pools and are asked for separately. */
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
