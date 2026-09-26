package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The allowlist that keeps a caller's string out of ORDER BY. {@code NULLIF} guards the columns where
 * Apollo encodes "unknown" as zero, which {@code NULLS LAST} would otherwise not sink.
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

    /** {@code NULLS LAST} either way: revenue is blank on nine rows in ten. */
    public String orderByTerms(SortDirection direction) {
        return String.join(", ", columns.stream()
                .map(column -> column + " " + direction.sqlKeyword() + " NULLS LAST")
                .toList());
    }

    public static CompanySortField fromValue(String value) {
        return ApiValueEnum.fromValue(CompanySortField.class, value);
    }
}
