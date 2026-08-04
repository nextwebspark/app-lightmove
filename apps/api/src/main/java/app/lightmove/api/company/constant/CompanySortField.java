package app.lightmove.api.company.constant;

import java.util.List;

/**
 * The columns a scoped company list can be sorted by. This enum is the allowlist that keeps a
 * caller-supplied string out of an ORDER BY: a request names a wire token, and only a token resolving
 * here ever reaches SQL. The free-text columns the Sourcing table can show (description, slogan,
 * specialties, tags) are deliberately absent — alphabetising a description answers no question.
 *
 * <p>Several constants order by a column the list does <i>not</i> display, because the displayed one
 * sorts wrong: {@code employee_range}/{@code revenue_range} are text, so {@code "1001-5000"} lands above
 * {@code "51-200"} and {@code "5M-25M"} above {@code "<5M"}.
 *
 * <p>{@code NULLIF} appears because this table encodes "we don't know" two ways — a null, and a zero
 * headcount/revenue or an empty city on a row whose range or country is still known. Only the null form
 * sinks under {@code NULLS LAST} on its own, so an ascending sort would otherwise open on the very rows
 * the ordering means to bury.
 */
public enum CompanySortField {

    NAME("name", List.of("name")),
    /**
     * Match quality, strongest first when ascending. Not a stored column: {@code match_tier} is the
     * label the scoped read computes per query, so this constant is only valid against a query that
     * projects it — {@code CompanyQueryService.search}, which is the only one that sorts.
     */
    TIER("tier", List.of("CASE match_tier WHEN 'DIRECT' THEN 0 WHEN 'ADJACENT' THEN 1 ELSE 2 END")),
    SECTOR("sector", List.of("NULLIF(primary_industry, '')")),
    EMPLOYEES("employees", List.of("NULLIF(employee_count, 0)")),
    REVENUE("revenue", List.of("NULLIF(revenue_usd, 0)")),
    LOCATION("location", List.of("NULLIF(hq_city, '')", "NULLIF(hq_country, '')")),
    FOUNDED("founded", List.of("NULLIF(founded, 0)")),
    COUNTRY("country", List.of("NULLIF(hq_country, '')"));

    private final String wireToken;
    private final List<String> columns;

    CompanySortField(String wireToken, List<String> columns) {
        this.wireToken = wireToken;
        this.columns = columns;
    }

    /** The wire value; the frontend mirror carries the same tokens. */
    public String value() {
        return wireToken;
    }

    /**
     * The ORDER BY terms for this field in the given direction. {@code NULLS LAST} regardless of
     * direction: a missing figure is a data gap, and a page of blanks is never what "sort by revenue"
     * was asking for.
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
