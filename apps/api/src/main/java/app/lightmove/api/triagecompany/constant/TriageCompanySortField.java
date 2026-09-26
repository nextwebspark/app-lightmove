package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The columns a mandate's triaged companies can be sorted by — the allowlist that keeps a
 * caller-supplied string out of an ORDER BY. The wire tokens deliberately match
 * {@code CompanySortField}'s, because the Companies and Strategy grids are one table over two sources.
 *
 * <p>Unlike Strategy's, these are <b>JPA property names</b> rather than SQL fragments: Spring Data
 * builds the ORDER BY, so there is no string to inject into. {@link #ADDED} exists only here — when a
 * company entered this mandate is a fact about the decision, not about the market.
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

    /** The entity property Spring Data orders by — never the caller's string. */
    private final String property;

    public static TriageCompanySortField fromValue(String value) {
        return ApiValueEnum.fromValue(TriageCompanySortField.class, value);
    }
}
