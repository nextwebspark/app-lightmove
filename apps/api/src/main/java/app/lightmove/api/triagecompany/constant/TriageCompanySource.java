package app.lightmove.api.triagecompany.constant;

/**
 * Which door a company came through into a mandate's universe.
 *
 * <p>This is provenance the grid shows, not bookkeeping. A headcount exported by Apollo and one typed
 * in by a researcher from a company's own careers page are not equally trustworthy, and a consultant
 * reading a row should be able to tell which they are looking at before acting on it.
 *
 * <p>The distinction is also a write rule: only {@link #STRATEGY} rows carry an
 * {@code apolloAccountId}, because only they were taken out of a universe that has ids. V34's
 * {@code app_lm_project_triage_company_apollo_source_chk} enforces that half in the schema.
 */
public enum TriageCompanySource {

    /** Taken out of the Apollo universe from the Strategy screen, one row or a whole filter at a time. */
    STRATEGY("strategy"),

    /** Typed in on the Companies screen — a company the market export does not carry. */
    MANUAL("manual"),

    /** Captured off a live page by the browser plugin. */
    EXTENSION("extension"),

    /**
     * Read out of a spreadsheet a consultant imported. Like MANUAL it carries no universe id — the
     * name in a file is all there is to identify a company by — and it is kept distinct so the grid's
     * Source badge can say that a headcount came out of somebody's export rather than being typed in
     * after checking. V47 added it to the column's CHECK; V36 had already reserved the same spelling on
     * the candidate side.
     */
    CSV("csv");

    private final String wireToken;

    TriageCompanySource(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The wire value; the frontend's source labels carry the same tokens. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its source, or {@code null} if unknown. */
    public static TriageCompanySource fromValue(String value) {
        for (TriageCompanySource source : values()) {
            if (source.wireToken.equals(value)) {
                return source;
            }
        }
        return null;
    }
}
