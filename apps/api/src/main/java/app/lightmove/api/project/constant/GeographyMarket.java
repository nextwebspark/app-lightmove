package app.lightmove.api.project.constant;

/**
 * The markets a project's geography scope selects from — a fixed catalog of the countries the company
 * universe actually holds ({@code app_lm_companies.hq_country}). This enum is the source of truth:
 * {@link #value} is the ISO-3166 alpha-2 code verbatim, which is both the wire value and the exact join
 * key a later sourcing filter runs against {@code hq_country} / {@code markets} — no translation layer.
 * Display names ("UAE", "Saudi Arabia") live only in the frontend catalog, so UI copy can change
 * without an API break or a data migration.
 *
 * <p>Jordan and Egypt appear in the mockup but are deliberately absent here: the universe holds no
 * companies for them yet. When the pipeline expands, each is one constant here plus one frontend
 * catalog entry — the storage column is plain varchar, so no migration.
 *
 * <p>The frontend keeps a mirror of the same values for instant rendering; a drift test on each side
 * keeps the two in step.
 */
public enum GeographyMarket {

    UAE("AE", "United Arab Emirates"),
    SAUDI_ARABIA("SA", "Saudi Arabia"),
    KUWAIT("KW", "Kuwait"),
    QATAR("QA", "Qatar"),
    BAHRAIN("BH", "Bahrain"),
    OMAN("OM", "Oman");

    private final String isoCountryCode;
    private final String apolloCountryName;

    GeographyMarket(String isoCountryCode, String apolloCountryName) {
        this.isoCountryCode = isoCountryCode;
        this.apolloCountryName = apolloCountryName;
    }

    /** The ISO-3166 alpha-2 code — the wire value, and {@code app_lm_companies.hq_country} verbatim. */
    public String value() {
        return isoCountryCode;
    }

    /**
     * The same market as {@code app_lm_apollo_companies.company_country} spells it. A second source's
     * join key, not a display name: Apollo writes countries out in full where the warehouse holds ISO
     * codes, so a report reading Apollo has to translate. UI copy stays in the frontend catalog, for
     * the reason above — this is the value the database compares, and it changes only if Apollo does.
     */
    public String apolloCountryName() {
        return apolloCountryName;
    }

    /** Resolve a wire value (ISO country code) to its market, or {@code null} if unknown. */
    public static GeographyMarket fromValue(String value) {
        for (GeographyMarket market : values()) {
            if (market.isoCountryCode.equals(value)) {
                return market;
            }
        }
        return null;
    }
}
