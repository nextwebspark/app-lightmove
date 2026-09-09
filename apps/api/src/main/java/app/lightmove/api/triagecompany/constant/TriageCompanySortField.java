package app.lightmove.api.triagecompany.constant;

/**
 * The columns a mandate's triaged companies can be sorted by — the allowlist that keeps a
 * caller-supplied string out of an ORDER BY. The wire tokens deliberately match
 * {@code CompanySortField}'s, because the Companies and Strategy grids are one table over two sources.
 *
 * <p>Unlike Strategy's, these are <b>JPA property names</b> rather than SQL fragments: Spring Data
 * builds the ORDER BY, so there is no string to inject into. {@link #ADDED} exists only here — when a
 * company entered this mandate is a fact about the decision, not about the market.
 */
public enum TriageCompanySortField {

    NAME("name", "companyName"),
    SECTOR("sector", "industry"),
    COUNTRY("country", "companyCountry"),
    LOCATION("location", "companyCity"),
    EMPLOYEES("employees", "numEmployees"),
    REVENUE("revenue", "annualRevenue"),
    FOUNDED("founded", "foundedYear"),
    ADDED("added", "createdAt");

    private final String wireToken;
    private final String property;

    TriageCompanySortField(String wireToken, String property) {
        this.wireToken = wireToken;
        this.property = property;
    }

    public String value() {
        return wireToken;
    }

    /** The entity property Spring Data orders by — never the caller's string. */
    public String property() {
        return property;
    }

    public static TriageCompanySortField fromValue(String value) {
        for (TriageCompanySortField field : values()) {
            if (field.wireToken.equals(value)) {
                return field;
            }
        }
        return null;
    }
}
