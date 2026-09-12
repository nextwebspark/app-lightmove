package app.lightmove.api.strategy.model;

import java.util.List;

/**
 * One filtered read's criteria over the Apollo company universe, resolved server-side from the
 * mandate's saved filter. An empty list means "no constraint on this axis", never "match nothing".
 *
 * <ul>
 *   <li>{@code employeeBands} / {@code revenueBands} are slugs ({@code "1k-5k"}, {@code "unknown"}),
 *       resolved into numeric bounds — Apollo ships raw figures, not pre-bucketed strings.
 *   <li>{@code employeeRange} / {@code revenueRange} take precedence over their axis's band list when
 *       set. Non-null <i>is</i> the custom-range mode; there is no flag that could disagree with it.
 *   <li>{@code countries} are Apollo's spelled-out names ("United Arab Emirates"), not ISO codes.
 *   <li>{@code marketSegments} are segment names resolved to keyword aliases, because the universe
 *       expresses go-to-market through a free-text {@code keywords} array rather than a column.
 *   <li>{@code offLimitsAccountIds} excludes unconditionally — no toggle, no flagged-but-visible row.
 *   <li>{@code nameQuery} changes which companies match, so the total count applies it too.
 * </ul>
 */
public record CompanyScope(List<String> industries, List<String> keywords,
                           List<String> marketSegments, List<String> countries,
                           List<String> employeeBands, List<String> revenueBands,
                           NumericRange employeeRange, NumericRange revenueRange,
                           List<String> offLimitsAccountIds, String nameQuery) {

    public CompanyScope {
        nameQuery = nameQuery == null || nameQuery.isBlank() ? null : nameQuery.trim();
    }

    /** The whole universe, narrowed by nothing — what an aggregate over the market as a whole reads. */
    public static CompanyScope unfiltered() {
        return new CompanyScope(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), null);
    }
}
