package app.lightmove.api.triagecompany.constant;

/**
 * Which door a company came through into a mandate's universe — provenance the grid shows.
 *
 * <p>It is also a write rule: only {@link #STRATEGY} rows are guaranteed an
 * {@code apolloAccountId}, and V34's
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
     * Read out of an imported spreadsheet. Carries no universe id, and is kept distinct from MANUAL so
     * the Source badge can say a figure came out of an export rather than being checked by hand.
     * V47 added it to the column's CHECK; V36 had reserved the spelling on the candidate side.
     */
    CSV("csv");

    private final String wireToken;

    TriageCompanySource(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }

    public static TriageCompanySource fromValue(String value) {
        for (TriageCompanySource source : values()) {
            if (source.wireToken.equals(value)) {
                return source;
            }
        }
        return null;
    }
}
