package app.lightmove.api.strategy.constant;

/**
 * One counted axis of the Strategy filter sidebar — the five accordions whose rows carry a number.
 *
 * <p>The axis exists because a facet count is taken with every criterion applied <b>except its
 * own</b>. Selecting United Arab Emirates should recount industries under the UAE, while the Location
 * rows keep counting under the other axes only — otherwise every country but the chosen one reads
 * zero and the accordion becomes unusable after the first click. Naming the axes makes that rule
 * something the query builder can state once rather than five near-copies of a WHERE clause.
 *
 * <p>{@link #wireName()} is the key the counts response is grouped under, and it matches the field
 * the client reads. Company keywords are deliberately absent: they are a typeahead over
 * {@code app_lm_apollo_keywords}, not an accordion, and their counts stay over the whole universe.
 */
public enum FacetAxis {

    INDUSTRY("industries"),
    COUNTRY("countries"),
    EMPLOYEE_SIZE("employeeBands"),
    REVENUE("revenueBands"),
    MARKET_SEGMENT("marketSegments");

    private final String wireName;

    FacetAxis(String wireName) {
        this.wireName = wireName;
    }

    /** The key this axis's counts arrive under, matching the response field the client reads. */
    public String wireName() {
        return wireName;
    }
}
