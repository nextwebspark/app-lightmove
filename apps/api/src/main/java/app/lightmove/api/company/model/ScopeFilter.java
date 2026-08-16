package app.lightmove.api.company.model;

import java.util.List;

/**
 * One scoped read's full set of criteria, bundled because {@code CompanyQueryService.estimate}/
 * {@code search} had outgrown a positional-parameter list. {@code directSectors}/{@code adjacentSectors}
 * stay separate (rather than one combined list) purely so a matched row can report which bucket it came
 * through. {@code employeeBands}/{@code revenueBands} are the wire-format range strings verbatim (e.g.
 * {@code "1-10"}, {@code "<5M"}) — matched directly against the {@code employee_range}/
 * {@code revenue_range} columns, never against the raw numeric {@code employee_count}/
 * {@code revenue_usd} figures (those can be zero or missing on a row whose range is still known).
 * {@code markets} are ISO-3166 alpha-2 codes, matched against {@code hq_country} or the {@code markets}
 * array. {@code targetKeys} and {@code offLimitsKeys} companies are both excluded outright, regardless
 * of any other match — targets are surfaced in the universe rather than the Sourcing list, off-limits
 * are barred by mandate. {@code nameQuery} narrows the result to a case-insensitive substring of
 * {@code name}; it lives here rather than beside the sort because it changes <i>which</i> companies
 * match, so {@code estimate} has to apply it too — a count taken without it would advertise thousands
 * of matches over a dozen listed rows.
 */
public record ScopeFilter(List<String> directSectors, List<String> adjacentSectors, List<String> tags,
                          List<String> employeeBands, List<String> revenueBands, List<String> markets,
                          List<CompanyKey> targetKeys, List<CompanyKey> offLimitsKeys, String nameQuery) {

    public ScopeFilter {
        nameQuery = nameQuery == null || nameQuery.isBlank() ? null : nameQuery.trim();
    }
}
