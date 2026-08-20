package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A mandate's saved filter — the whole Strategy sidebar in one value, stored as the {@code filter}
 * jsonb column on {@code app_lm_strategy} and again on every saved search.
 *
 * <p>Each list holds wire tokens, never display labels: Apollo industry values, market-segment names,
 * Apollo country names, and the band slugs from {@code EmployeeBand} / {@code RevenueBand}. Labels
 * are presentation and will be retitled; a stored filter that stopped resolving because a row was
 * renamed would be a silent scope change on a live mandate.
 *
 * <p><b>Groups are never stored.</b> Selecting a sector group expands to its industries client-side
 * and this record keeps those, so re-tuning {@code sector-taxonomy.json} cannot widen the scope of a
 * search saved months ago.
 *
 * <p><b>Bands and ranges are the two modes of one axis.</b> {@code employeeRange} non-null means the
 * sidebar is in Custom Range mode and {@code employeeBands} is ignored; null means the predefined
 * rows are in force. Same for revenue. There is no mode flag — the shape of the data is the mode, so
 * the two cannot contradict each other.
 *
 * <p>An empty list means "no constraint on this axis", not "match nothing" — an untouched filter is
 * the whole universe, which is the right opening state for a search screen.
 *
 * <p>{@code @JsonIgnoreProperties} is load-bearing rather than decorative. This record is read back
 * out of a jsonb column that already holds documents written by earlier versions of this type — the
 * dropped {@code includeOffLimits} flag among them — and a stored filter must never become
 * unreadable because a field was retired. Off-limits companies are now always excluded, which is
 * what the wireframe's panel says they are.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrategyFilter(List<String> industries, List<String> marketSegments,
                             List<String> countries, List<String> employeeBands,
                             List<String> revenueBands, NumericRange employeeRange,
                             NumericRange revenueRange) {

    /**
     * Null-tolerant on the way in: a document written before a field existed, or one Jackson filled
     * only partially, reads as an unconstrained axis rather than throwing on the next request. An
     * empty range normalises to null so "custom mode, nothing typed yet" cannot be stored as a
     * constraint that matches everything by accident.
     */
    public StrategyFilter {
        industries = industries == null ? List.of() : List.copyOf(industries);
        marketSegments = marketSegments == null ? List.of() : List.copyOf(marketSegments);
        countries = countries == null ? List.of() : List.copyOf(countries);
        employeeBands = employeeBands == null ? List.of() : List.copyOf(employeeBands);
        revenueBands = revenueBands == null ? List.of() : List.copyOf(revenueBands);
        employeeRange = employeeRange != null && employeeRange.isEmpty() ? null : employeeRange;
        revenueRange = revenueRange != null && revenueRange.isEmpty() ? null : revenueRange;
    }

    /** The whole universe, off-limits barred — what a strategy reads as before its first save. */
    public static StrategyFilter empty() {
        return new StrategyFilter(List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }
}
