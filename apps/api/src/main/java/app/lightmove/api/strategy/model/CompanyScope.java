package app.lightmove.api.strategy.model;

import java.util.List;

/**
 * One filtered read's full set of criteria over the Apollo company universe — what the Strategy
 * sidebar's chips add up to, resolved server-side from the mandate's saved filter.
 *
 * <p>Replaces the warehouse-era {@code ScopeFilter}, and the differences are all consequences of the
 * new source and the new screen:
 *
 * <ul>
 *   <li><b>One industry list, not three.</b> The old shape split sectors into direct / adjacent /
 *       inferred so a matched row could report which bucket it came through, feeding a Fit score.
 *       The new filter is flat pass-or-fail — every visible row matches everything selected — so
 *       there is no bucket to report.
 *   <li><b>{@code employeeBands} and {@code revenueBands} are slugs</b> ({@code "1k-5k"},
 *       {@code "unknown"}), resolved through {@link app.lightmove.api.strategy.constant.EmployeeBand}
 *       / {@link app.lightmove.api.strategy.constant.RevenueBand} into numeric bounds. Apollo ships
 *       raw figures, not pre-bucketed range strings.
 *   <li><b>{@code marketSegments} are segment names</b> ("B2B", "SaaS"), not keywords. The service
 *       resolves each to its keyword aliases through {@code MarketSegments}, because the universe
 *       expresses go-to-market through a free-text {@code keywords} array rather than a column.
 *   <li><b>{@code countries} are Apollo's spelled-out names</b> ("United Arab Emirates"), not ISO
 *       codes. The live universe holds exactly six values, all GCC, which is why the mockup's six
 *       Location chips are literally the whole vocabulary.
 *   <li><b>{@code employeeRange} / {@code revenueRange} are the custom-range mode</b> and take
 *       precedence over their axis's band list when set. Non-null <i>is</i> the mode; there is no
 *       flag that could disagree with the data.
 *   <li><b>{@code offLimitsAccountIds} is an exclusion list, unconditionally.</b> A barred company
 *       never appears in a filtered read — no toggle, no flagged-but-visible row — because that is
 *       what the Off-limits panel tells the consultant it does.
 * </ul>
 *
 * <p>An empty list means "no constraint on this axis", never "match nothing" — the whole universe is
 * a legitimate starting point for a search screen, unlike the old scope, which refused to answer
 * without a sector.
 *
 * <p>{@code nameQuery} narrows to a case-insensitive substring of the company name. It lives here
 * rather than beside the sort because it changes <i>which</i> companies match, so the count has to
 * apply it too — a total taken without it would advertise thousands of matches over a dozen rows.
 */
public record CompanyScope(List<String> industries, List<String> marketSegments, List<String> countries,
                           List<String> employeeBands, List<String> revenueBands,
                           NumericRange employeeRange, NumericRange revenueRange,
                           List<String> offLimitsAccountIds, String nameQuery) {

    public CompanyScope {
        nameQuery = nameQuery == null || nameQuery.isBlank() ? null : nameQuery.trim();
    }

    /** The same scope narrowed by a caller's name filter, leaving the stored criteria untouched. */
    public CompanyScope withNameQuery(String query) {
        return new CompanyScope(industries, marketSegments, countries, employeeBands, revenueBands,
                employeeRange, revenueRange, offLimitsAccountIds, query);
    }
}
