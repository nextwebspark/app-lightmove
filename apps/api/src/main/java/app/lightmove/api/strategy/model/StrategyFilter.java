package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A mandate's saved filter, stored as the {@code filter} jsonb column on {@code app_lm_strategy} and
 * again on every saved search. Each list holds wire tokens, never display labels: a stored filter
 * that stopped resolving because a row was renamed would be a silent scope change on a live mandate.
 * Sector groups expand client-side and are never stored, so re-tuning the taxonomy cannot widen a
 * search saved months ago.
 *
 * <p>Bands and ranges are the two modes of one axis: a non-null range means Custom Range and the band
 * list is ignored. The shape of the data is the mode, so the two cannot contradict each other.
 *
 * <p>{@code @JsonIgnoreProperties} is load-bearing rather than decorative. This record is read back
 * out of a jsonb column that already holds documents written by earlier versions of this type — the
 * dropped {@code includeOffLimits} flag among them — and a stored filter must never become
 * unreadable because a field was retired.
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
