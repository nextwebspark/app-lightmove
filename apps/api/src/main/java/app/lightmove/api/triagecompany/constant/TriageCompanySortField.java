package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The sort allowlist, mapping wire tokens (matching {@code CompanySortField}'s) to JPA property names
 * that Spring Data orders by — no caller string reaches an ORDER BY.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TriageCompanySortField implements ApiValueEnum {

    NAME("name", "companyName"),
    SECTOR("sector", "industry"),
    COUNTRY("country", "companyCountry"),
    LOCATION("location", "companyCity"),
    EMPLOYEES("employees", "numEmployees"),
    REVENUE("revenue", "annualRevenue"),
    FOUNDED("founded", "foundedYear"),
    ADDED("added", "createdAt");

    private final String value;

    private final String property;

    public static TriageCompanySortField fromValue(String value) {
        return ApiValueEnum.fromValue(TriageCompanySortField.class, value);
    }
}
