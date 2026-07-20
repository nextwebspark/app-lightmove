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

    UAE("AE"),
    SAUDI_ARABIA("SA"),
    KUWAIT("KW"),
    QATAR("QA"),
    BAHRAIN("BH"),
    OMAN("OM");

    private final String isoCountryCode;

    GeographyMarket(String isoCountryCode) {
        this.isoCountryCode = isoCountryCode;
    }

    /** The ISO-3166 alpha-2 code — the wire value, and {@code app_lm_companies.hq_country} verbatim. */
    public String value() {
        return isoCountryCode;
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
