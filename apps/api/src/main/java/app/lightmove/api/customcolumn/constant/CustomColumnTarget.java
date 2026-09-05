package app.lightmove.api.customcolumn.constant;

/**
 * Which half of a Companies-grid row a custom column describes.
 *
 * <p>A row on that screen is a <i>person at a company</i>, so an extra column is always a fact about
 * one or the other and never about both: "Founded" belongs to the company, "Ethnicity" to the person.
 * Without this distinction an import would have to guess which record an unmapped column lands on, and
 * a company with three executives would either repeat a company fact three times or scatter a personal
 * one across the wrong rows.
 */
public enum CustomColumnTarget {

    /** A fact about the company — stored on the mandate's triage row. */
    COMPANY("company"),

    /** A fact about the person — stored on the mandate's candidate row. */
    CANDIDATE("candidate");

    private final String wireToken;

    CustomColumnTarget(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The wire value; the frontend addresses columns by the same tokens. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its target, or {@code null} if unknown. */
    public static CustomColumnTarget fromValue(String value) {
        for (CustomColumnTarget target : values()) {
            if (target.wireToken.equals(value)) {
                return target;
            }
        }
        return null;
    }
}
