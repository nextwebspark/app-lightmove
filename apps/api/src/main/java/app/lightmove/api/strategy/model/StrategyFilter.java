package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A mandate's saved filter, the {@code filter} jsonb on the strategy and every saved search. Lists
 * hold wire tokens, never labels or sector groups, so a rename or re-tuned taxonomy cannot silently
 * change a live scope. A non-null range overrides its axis's bands. {@code ignoreUnknown} keeps
 * documents with retired fields (e.g. {@code includeOffLimits}) readable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrategyFilter(List<String> industries, List<String> keywords,
                             List<String> marketSegments, List<String> countries,
                             List<String> employeeBands, List<String> revenueBands,
                             NumericRange employeeRange, NumericRange revenueRange) {

    /** Null-tolerant: a document written before a field existed reads as an unconstrained axis. */
    public StrategyFilter {
        industries = industries == null ? List.of() : List.copyOf(industries);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        marketSegments = marketSegments == null ? List.of() : List.copyOf(marketSegments);
        countries = countries == null ? List.of() : List.copyOf(countries);
        employeeBands = employeeBands == null ? List.of() : List.copyOf(employeeBands);
        revenueBands = revenueBands == null ? List.of() : List.copyOf(revenueBands);
        employeeRange = employeeRange != null && employeeRange.isEmpty() ? null : employeeRange;
        revenueRange = revenueRange != null && revenueRange.isEmpty() ? null : revenueRange;
    }

    public static StrategyFilter empty() {
        return new StrategyFilter(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null);
    }
}
