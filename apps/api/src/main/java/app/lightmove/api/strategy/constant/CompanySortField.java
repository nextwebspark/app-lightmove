package app.lightmove.api.strategy.constant;

import java.util.List;

/**
 * The columns a scoped company list can be sorted by. This enum is the allowlist that keeps a
 * caller-supplied string out of an ORDER BY: a request names a wire token, and only a token resolving
 * here ever reaches SQL. The free-text columns the table can show — {@code short_description} behind
 * the Notes column — are deliberately absent; alphabetising a description answers no question.
 *
 * <p>{@code NULLIF} guards the columns where Apollo encodes "we don't know" as a zero rather than a
 * null. Only the null form sinks under {@code NULLS LAST} on its own, so an ascending sort would
 * otherwise open on the very rows the ordering means to bury.
 */
public enum CompanySortField {

    NAME("name", List.of("company_name")),
    SECTOR("sector", List.of("NULLIF(industry, '')")),
    COUNTRY("country", List.of("NULLIF(company_country, '')")),
    LOCATION("location", List.of("NULLIF(company_city, '')", "NULLIF(company_country, '')")),
    EMPLOYEES("employees", List.of("NULLIF(num_employees, 0)")),
    REVENUE("revenue", List.of("NULLIF(annual_revenue, 0)")),
    FOUNDED("founded", List.of("NULLIF(founded_year, 0)"));

    private final String wireToken;
    private final List<String> columns;

    CompanySortField(String wireToken, List<String> columns) {
        this.wireToken = wireToken;
        this.columns = columns;
    }

    /** The wire value; the frontend column definitions carry the same tokens as their column ids. */
    public String value() {
        return wireToken;
    }

    /**
     * The ORDER BY terms for this field in the given direction. {@code NULLS LAST} regardless of
     * direction: a missing figure is a data gap, and a page of blanks is never what "sort by revenue"
     * was asking for. That matters more here than it did against the warehouse — Apollo publishes a
     * revenue figure on one row in ten, so an ascending revenue sort without this is nine pages of
     * nothing.
     */
    public String orderByTerms(SortDirection direction) {
        return String.join(", ", columns.stream()
                .map(column -> column + " " + direction.sqlKeyword() + " NULLS LAST")
                .toList());
    }

    /** Resolve a wire value to its field, or {@code null} if unknown. */
    public static CompanySortField fromValue(String value) {
        for (CompanySortField field : values()) {
            if (field.wireToken.equals(value)) {
                return field;
            }
        }
        return null;
    }
}
