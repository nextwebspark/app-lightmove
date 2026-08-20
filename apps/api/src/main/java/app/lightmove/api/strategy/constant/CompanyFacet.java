package app.lightmove.api.strategy.constant;

/**
 * The five axes the Strategy filter sidebar offers, one accordion each.
 *
 * <p>There is no {@code OWNERSHIP}: {@code app_lm_apollo_companies} carries no ownership, org_type or
 * ipo_status column, and the mockup's Ownership Type accordion has nothing behind it. Deriving one
 * from {@code latest_funding} (2,123 of 71,822 rows) or {@code parent_company} (1,811) would label
 * most of the universe wrong, so the accordion is not shipped rather than shipped empty.
 *
 * <p>Off-limits is absent for a different reason: it is not a property of a company but a decision a
 * mandate made about one, so it is a boolean on the filter rather than a facet with values to count.
 */
public enum CompanyFacet {

    INDUSTRY("industry"),
    /**
     * How a company goes to market. Not a column: the universe expresses this through
     * {@code keywords}, which is why {@code MarketSegments} owns the mapping — see its doc.
     */
    MARKET_SEGMENT("marketSegment"),
    COUNTRY("country"),
    EMPLOYEE_BAND("employeeBand"),
    REVENUE_BAND("revenueBand");

    private final String wireToken;

    CompanyFacet(String wireToken) {
        this.wireToken = wireToken;
    }

    /** The wire value; also the key this facet's counts arrive under in the response. */
    public String value() {
        return wireToken;
    }

    /** Resolve a wire value to its facet, or {@code null} if unknown. */
    public static CompanyFacet fromValue(String value) {
        for (CompanyFacet facet : values()) {
            if (facet.wireToken.equals(value)) {
                return facet;
            }
        }
        return null;
    }
}
