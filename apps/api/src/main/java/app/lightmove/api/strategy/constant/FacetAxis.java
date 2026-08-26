package app.lightmove.api.strategy.constant;

/**
 * One counted axis of the Strategy filter sidebar — the four accordions whose rows carry a number.
 *
 * <p>The axis exists because a facet count is taken with every criterion applied <b>except its
 * own</b>. Selecting <i>banking</i> should recount the headcount bands and segments under it while
 * leaving the other industries countable — apply the industry to its own accordion and every row but
 * the chosen one reads zero, which makes Industry unusable the moment it is used. Naming the axes
 * makes that rule something the query builder can state once rather than four near-copies of a WHERE
 * clause.
 *
 * <p>{@link #wireName()} is the key the counts response is grouped under, and it matches the field
 * the client reads.
 *
 * <p><b>Location is deliberately absent</b>, and so are company keywords. Neither is counted:
 * Location offers its six countries as unnumbered pills ({@link
 * app.lightmove.api.strategy.dto.FacetValue}), and keywords are a typeahead over
 * {@code app_lm_apollo_keywords} whose counts stay over the whole universe. A criterion no accordion
 * counts is never excluded from anything, so it is simply always applied.
 */
public enum FacetAxis {

    INDUSTRY("industries"),
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
