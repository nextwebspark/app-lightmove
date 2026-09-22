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
    CSV("csv"),

    /**
     * Filed by accepting a proposal the assistant made. V65's CHECK already permitted the spelling;
     * only this constant was missing.
     *
     * <p>Distinct from {@link #STRATEGY} although the row it files may have come out of the same
     * universe, because {@code source} records the <i>door</i>, and a consultant asking a question in
     * a chat panel is not a consultant ticking a row on the Strategy grid. It is also the only badge
     * that says a machine chose the company and a person only agreed.
     */
    ASSISTANT("assistant"),

    /**
     * Found by AI Research's grounded web search and filed by the person who read the row. V68 added
     * it to the column's CHECK.
     *
     * <p>Distinct from {@link #ASSISTANT} although a machine chose both: that badge says a
     * conversation proposed the company, this one says a search over the open web did, and the two
     * are read differently by whoever picks the row up later. It is also the only door whose figures
     * may be absent by design — a company neither the universe nor a vendor carries is filed with
     * its name and nothing else, because the alternative is a number the model invented.
     */
    WEB("web");

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
