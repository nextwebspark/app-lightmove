package app.lightmove.api.strategy.model;

import java.util.List;

/**
 * One filtered read's criteria over the Apollo universe. An empty list means "no constraint on this
 * axis", never "match nothing"; a non-null range overrides its axis's band list.
 *
 * <p>{@code triagedExclusion} is set only by the Strategy search. The bulk triage writes leave it
 * {@link CompanyExclusion#NONE}: they dedupe against held companies themselves and report an "already
 * there" count that pre-filtering here would silently zero out.
 */
public record CompanyScope(List<String> industries, List<String> keywords,
                           List<String> marketSegments, List<String> countries,
                           List<String> employeeBands, List<String> revenueBands,
                           NumericRange employeeRange, NumericRange revenueRange,
                           List<String> offLimitsAccountIds, CompanyExclusion triagedExclusion,
                           String nameQuery) {

    public CompanyScope {
        nameQuery = nameQuery == null || nameQuery.isBlank() ? null : nameQuery.trim();
    }

    public static CompanyScope unfiltered() {
        return new CompanyScope(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), CompanyExclusion.NONE, null);
    }
}
