package app.lightmove.api.company.service;

import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.model.ScopeBreakdown;
import app.lightmove.api.company.model.ScopeFilter;
import app.lightmove.api.project.constant.GeographyMarket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read-only aggregates over the Apollo company universe ({@code app_lm_apollo_companies}) — the source
 * behind a mandate's report. A sibling of {@link CompanyQueryService} rather than a mode of it: the two
 * tables answer the same questions from different vocabularies, and a single service switching on which
 * one it was pointed at would be two query builders sharing a name.
 *
 * <p>It takes the very same {@link ScopeFilter} the Strategy produces, so a mandate still has one saved
 * scope and each source interprets it. Every dimension needs translating, and each translation is a
 * fact about Apollo rather than a preference:
 *
 * <ul>
 *   <li><b>Sector and tags</b> match case-insensitively — Apollo's industries are lower-cased
 *       throughout, so the Title Case labels the Strategy screen stores would match nothing exactly.
 *   <li><b>Markets</b> resolve through {@link GeographyMarket#apolloCountryName()}: Apollo spells
 *       countries out where the warehouse holds ISO codes.
 *   <li><b>Size bands</b> become numeric bounds. This is the one place the product computes them —
 *       {@link CompanyQueryService} deliberately does not, because the warehouse ships the same bands
 *       as strings; Apollo ships only raw {@code num_employees} / {@code annual_revenue}.
 *   <li><b>Target and off-limits lists</b> cannot be applied at all: they are keyed on
 *       {@code (source, source_id)}, which Apollo has no counterpart for. The report states that
 *       rather than quietly reporting a universe that still contains barred companies.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ApolloCompanyQueryService {

    /** Weakest-to-strongest is meaningless for a legend; this is the order the Strategy screen reads in. */
    private static final List<String> MATCH_TIER_ORDER = List.of("DIRECT", "ADJACENT", "INFERRED");

    private final JdbcClient jdbc;

    /** How many companies match the scope. An empty scope matches nothing without a query. */
    public long estimate(ScopeFilter scope) {
        WhereClause where = buildWhere(scope);
        if (where == null) {
            return 0;
        }
        return bind(jdbc.sql("SELECT count(*) FROM app_lm_apollo_companies WHERE " + where.sql()),
                where.params()).query(Long.class).single();
    }

    /** The scope's most populous industries, largest first. */
    public List<ScopeBreakdown> countBySector(ScopeFilter scope, int limit) {
        return breakdown(scope, "industry", Map.of(), "industry IS NOT NULL AND industry <> ''", limit);
    }

    /** The scope's most populous countries, largest first. */
    public List<ScopeBreakdown> countByCountry(ScopeFilter scope, int limit) {
        return breakdown(scope, "company_country", Map.of(),
                "company_country IS NOT NULL AND company_country <> ''", limit);
    }

    /** The scope's most populous cities, largest first. */
    public List<ScopeBreakdown> countByCity(ScopeFilter scope, int limit) {
        return breakdown(scope, "company_city", Map.of(),
                "company_city IS NOT NULL AND company_city <> ''", limit);
    }

    /** How the scope splits across the match tiers, in the fixed direct → adjacent → inferred order. */
    public List<ScopeBreakdown> countByMatchTier(ScopeFilter scope) {
        Map<String, Object> tierParams = new LinkedHashMap<>();
        String expression = matchTierExpression(scope, tierParams);
        return breakdown(scope, expression, tierParams, null, null).stream()
                .sorted(Comparator.comparingInt(row -> MATCH_TIER_ORDER.indexOf(row.label())))
                .toList();
    }

    /**
     * Which of the given sector labels Apollo does not carry at all. The report states the count: a
     * mandate scoped entirely to sectors this source lacks otherwise reports a universe of zero, which
     * reads as a finding about the market rather than about the data behind it.
     */
    public List<String> sectorsAbsentFromSource(List<String> sectors) {
        if (sectors.isEmpty()) {
            return List.of();
        }
        Set<String> present = Set.copyOf(jdbc.sql("""
                        SELECT DISTINCT lower(industry)
                        FROM app_lm_apollo_companies
                        WHERE industry IS NOT NULL AND lower(industry) IN (:sectors)
                        """)
                .param("sectors", lowered(sectors))
                .query(String.class)
                .list());
        return sectors.stream().filter(sector -> !present.contains(lower(sector))).toList();
    }

    /**
     * The shared shape behind every grouped aggregate: the scope's WHERE clause, grouped by one
     * expression. {@code presenceCondition} drops rows the grouping column is missing on, since a bar
     * labelled with a blank is noise rather than a finding.
     */
    private List<ScopeBreakdown> breakdown(ScopeFilter scope, String expression,
                                           Map<String, Object> expressionParams, String presenceCondition,
                                           Integer limit) {
        WhereClause where = buildWhere(scope);
        if (where == null) {
            return List.of();
        }
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        params.putAll(expressionParams);
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies
                WHERE %s
                GROUP BY 1
                ORDER BY count(*) DESC, 1
                """.formatted(expression,
                presenceCondition == null ? where.sql() : where.sql() + " AND " + presenceCondition);
        if (limit != null) {
            sql = sql + "LIMIT :groupLimit";
            params.put("groupLimit", limit);
        }
        return bind(jdbc.sql(sql), params).query(ScopeBreakdown.class).list();
    }

    /**
     * {@code scopeMatch AND size AND geography}, mirroring {@link CompanyQueryService}'s own builder:
     * the sector-or-tag match is the anchor and returns {@code null} without one, and each further
     * criterion is omitted entirely when its list is empty. The target and off-limits lists are absent
     * by necessity, not oversight — see the class doc.
     */
    private WhereClause buildWhere(ScopeFilter scope) {
        List<String> allSectors = new ArrayList<>(scope.directSectors());
        allSectors.addAll(scope.adjacentSectors());
        if (allSectors.isEmpty() && scope.tags().isEmpty()) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();

        List<String> scopeParts = new ArrayList<>();
        if (!allSectors.isEmpty()) {
            scopeParts.add("lower(industry) IN (:sectors)");
            params.put("sectors", lowered(allSectors));
        }
        if (!scope.tags().isEmpty()) {
            // Lower-cased on both sides: Apollo's keywords vocabulary is unrelated to the warehouse's
            // industry_tags, so casing is the one difference we can rule out cheaply.
            scopeParts.add("""
                    EXISTS (SELECT 1 FROM unnest(keywords) AS keyword
                            WHERE lower(keyword) IN (:tags))""");
            params.put("tags", lowered(scope.tags()));
        }
        clauses.add("(" + String.join(" OR ", scopeParts) + ")");

        String employeeClause = numericBandClause("num_employees", scope.employeeBands(),
                ApolloCompanyQueryService::employeeBounds, params, "emp");
        if (employeeClause != null) {
            clauses.add(employeeClause);
        }
        String revenueClause = numericBandClause("annual_revenue", scope.revenueBands(),
                ApolloCompanyQueryService::revenueBounds, params, "rev");
        if (revenueClause != null) {
            clauses.add(revenueClause);
        }
        if (!scope.markets().isEmpty()) {
            clauses.add("company_country IN (:markets)");
            params.put("markets", countryNamesOf(scope.markets()));
        }
        if (scope.nameQuery() != null) {
            clauses.add("company_name ILIKE :nameQuery ESCAPE '\\'");
            params.put("nameQuery", "%" + escapeLikePattern(scope.nameQuery()) + "%");
        }
        return new WhereClause(String.join(" AND ", clauses), params);
    }

    /**
     * A selected set of size bands as an OR of numeric ranges over one column. A row with no figure is
     * excluded: it cannot be shown to fall in the band, and Apollo carries {@code annual_revenue} on
     * roughly one row in ten, so including the unknowns would make the filter decorative. The report
     * states that exclusion rather than leaving it to be discovered.
     */
    private static String numericBandClause(String column, List<String> bandValues,
                                            Function<String, long[]> boundsOf,
                                            Map<String, Object> params, String prefix) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        for (String bandValue : bandValues) {
            long[] bounds = boundsOf.apply(bandValue);
            if (bounds == null) {
                continue;
            }
            String lowParam = prefix + "Low" + index;
            params.put(lowParam, bounds[0]);
            if (bounds[1] == NO_UPPER_BOUND) {
                ranges.add("%s >= :%s".formatted(column, lowParam));
            } else {
                String highParam = prefix + "High" + index;
                params.put(highParam, bounds[1]);
                ranges.add("%s BETWEEN :%s AND :%s".formatted(column, lowParam, highParam));
            }
            index++;
        }
        return ranges.isEmpty() ? null : "(" + String.join(" OR ", ranges) + ")";
    }

    /** Marks a top band that runs to infinity ("10000+", "5B+"). */
    private static final long NO_UPPER_BOUND = -1;

    /** Headcount bounds for a band's wire value, or null if it is not one we know. */
    private static long[] employeeBounds(String bandValue) {
        EmployeeBand band = EmployeeBand.fromValue(bandValue);
        if (band == null) {
            return null;
        }
        return switch (band) {
            case B_1_10 -> new long[] {1, 10};
            case B_11_50 -> new long[] {11, 50};
            case B_51_200 -> new long[] {51, 200};
            case B_201_500 -> new long[] {201, 500};
            case B_501_1000 -> new long[] {501, 1_000};
            case B_1001_5000 -> new long[] {1_001, 5_000};
            case B_5001_10000 -> new long[] {5_001, 10_000};
            case B_10000_PLUS -> new long[] {10_000, NO_UPPER_BOUND};
        };
    }

    /** Annual-revenue bounds in USD for a band's wire value, or null if it is not one we know. */
    private static long[] revenueBounds(String bandValue) {
        RevenueBand band = RevenueBand.fromValue(bandValue);
        if (band == null) {
            return null;
        }
        return switch (band) {
            case R_UNDER_5M -> new long[] {0, 4_999_999};
            case R_5M_25M -> new long[] {5_000_000, 24_999_999};
            case R_25M_100M -> new long[] {25_000_000, 99_999_999};
            case R_100M_500M -> new long[] {100_000_000, 499_999_999};
            case R_500M_1B -> new long[] {500_000_000, 999_999_999};
            case R_1B_5B -> new long[] {1_000_000_000L, 4_999_999_999L};
            case R_5B_PLUS -> new long[] {5_000_000_000L, NO_UPPER_BOUND};
        };
    }

    /** ISO codes as Apollo spells the same countries. An unknown code drops out rather than matching. */
    private static List<String> countryNamesOf(List<String> isoCodes) {
        return isoCodes.stream()
                .map(GeographyMarket::fromValue)
                .filter(Objects::nonNull)
                .map(GeographyMarket::apolloCountryName)
                .toList();
    }

    /**
     * Which bucket a row matched through. Falls through to {@code 'INFERRED'} for the same reason as
     * {@link CompanyQueryService}: the WHERE clause already guarantees a sector-or-tag match, so
     * anything neither sector list catches matched on a keyword.
     */
    private static String matchTierExpression(ScopeFilter scope, Map<String, Object> params) {
        if (scope.directSectors().isEmpty() && scope.adjacentSectors().isEmpty()) {
            return "'INFERRED'";
        }
        StringBuilder expression = new StringBuilder("CASE");
        if (!scope.directSectors().isEmpty()) {
            expression.append(" WHEN lower(industry) IN (:directSectors) THEN 'DIRECT'");
            params.put("directSectors", lowered(scope.directSectors()));
        }
        if (!scope.adjacentSectors().isEmpty()) {
            expression.append(" WHEN lower(industry) IN (:adjacentSectors) THEN 'ADJACENT'");
            params.put("adjacentSectors", lowered(scope.adjacentSectors()));
        }
        expression.append(" ELSE 'INFERRED' END");
        return expression.toString();
    }

    private static List<String> lowered(List<String> values) {
        return values.stream().map(ApolloCompanyQueryService::lower).toList();
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /** Backslash-escape LIKE's wildcards so the user's text matches literally. */
    private static String escapeLikePattern(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Map<String, Object> params) {
        JdbcClient.StatementSpec bound = spec;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            bound = bound.param(entry.getKey(), entry.getValue());
        }
        return bound;
    }
}
