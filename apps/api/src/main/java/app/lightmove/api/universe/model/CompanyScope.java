package app.lightmove.api.universe.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * One filtered read's criteria over the universe: an empty list is no constraint, a non-null range overrides its
 * band list. {@code triagedExclusion} is the Strategy search's alone — bulk writes dedupe themselves and count
 * "already there".
 */
@NamedInterface(name = "api", propagate = false)
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
