package app.lightmove.api.triagecompany.constant;

/**
 * The columns a mandate's triaged companies can be sorted by — the allowlist that keeps a
 * caller-supplied string out of an ORDER BY.
 *
 * <p>The wire tokens are deliberately the same ones {@code CompanySortField} uses, because the
 * Companies grid and the Strategy grid are the same table rendering two sources: a user who sorted
 * Strategy by {@code employees} and then opened Shortlisted means the same thing by the word.
 *
 * <p>Unlike Strategy's, these are <b>JPA property names</b> rather than SQL fragments: this list is a
 * few hundred rows read through the repository, not a filtered scan of 71,822, so Spring Data builds
 * the ORDER BY and there is no string to inject into. {@link #ADDED} exists here and not there — when
 * a company entered this mandate is a fact about the decision, which the market has no opinion on.
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

    /** The wire value; the frontend column definitions carry the same tokens as their column ids. */
    public String value() {
        return wireToken;
    }

    /** The entity property Spring Data orders by — never the caller's string. */
    public String property() {
        return property;
    }

    /** Resolve a wire value to its field, or {@code null} if unknown. */
    public static TriageCompanySortField fromValue(String value) {
        for (TriageCompanySortField field : values()) {
            if (field.wireToken.equals(value)) {
                return field;
            }
        }
        return null;
    }
}
