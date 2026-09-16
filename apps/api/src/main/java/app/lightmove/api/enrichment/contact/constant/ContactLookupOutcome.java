package app.lightmove.api.enrichment.contact.constant;

/**
 * What one press of a Find button did — and, read with the audit row's {@code billed} detail, what it
 * cost. {@link #HELD} and a remembered {@link #NONE} spend nothing: the answer came off the row.
 */
public enum ContactLookupOutcome {

    /** The provider answered with values. */
    FOUND("found"),

    /** The provider has nothing on this person. Remembered, so it is asked once and never again. */
    NONE("none"),

    /** We already had values. Nothing was asked. */
    HELD("held");

    private final String value;

    ContactLookupOutcome(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
