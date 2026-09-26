package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The columns a scoped company list can be sorted by — the allowlist that keeps a caller-supplied
 * string out of an ORDER BY. Free-text columns are deliberately absent.
 *
 * <p>{@code NULLIF} guards the columns where Apollo encodes "we don't know" as a zero rather than a
 * null. Only the null form sinks under {@code NULLS LAST}, so an ascending sort would otherwise open
 * on the very rows the ordering means to bury.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CompanySortField implements ApiValueEnum {

    NAME("name", List.of("company_name")),
    SECTOR("sector", List.of("NULLIF(industry, '')")),
    COUNTRY("country", List.of("NULLIF(company_country, '')")),
    LOCATION("location", List.of("NULLIF(company_city, '')", "NULLIF(company_country, '')")),
    EMPLOYEES("employees", List.of("NULLIF(num_employees, 0)")),
    REVENUE("revenue", List.of("NULLIF(annual_revenue, 0)")),
    FOUNDED("founded", List.of("NULLIF(founded_year, 0)"));

    private final String value;
    private final List<String> columns;

    /**
     * The ORDER BY terms for this field. {@code NULLS LAST} regardless of direction: Apollo publishes
     * a revenue figure on one row in ten, so an ascending revenue sort without it is nine pages of
     * blanks.
     */
    public String orderByTerms(SortDirection direction) {
        return String.join(", ", columns.stream()
                .map(column -> column + " " + direction.sqlKeyword() + " NULLS LAST")
                .toList());
    }

    public static CompanySortField fromValue(String value) {
        return ApiValueEnum.fromValue(CompanySortField.class, value);
    }
}
