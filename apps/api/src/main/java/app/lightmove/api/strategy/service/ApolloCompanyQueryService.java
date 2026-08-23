package app.lightmove.api.strategy.service;

import app.lightmove.api.core.config.CacheConfig;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.dto.SectorGroup;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Every read of the company universe. The universe is {@code app_lm_apollo_companies} — 71,822 GCC
 * companies loaded by the pipeline, read-only to this application — and it is the only one; the
 * brightdata warehouse copy this service used to sit beside is gone.
 *
 * <p>{@code JdbcClient} rather than JPA, for the reason the deleted sibling gave and this one
 * inherits: every useful read here is an aggregate or a filtered projection over ETL-owned reference
 * data, and an entity would buy identity, dirty checking and a lifecycle for rows the application
 * must never write.
 *
 * <p>Two things about Apollo shape everything below:
 *
 * <ul>
 *   <li><b>Size arrives raw.</b> {@code num_employees} and {@code annual_revenue} are figures, not
 *       pre-bucketed range strings, so a band selection becomes an OR of numeric ranges built from
 *       {@link EmployeeBand} / {@link RevenueBand}. Those enums own the bounds; this service owns
 *       only the SQL they turn into.
 *   <li><b>Revenue is sparse.</b> 7,132 rows in 71,822 carry a figure. {@link RevenueBand#R_UNKNOWN}
 *       is therefore a selectable band rendering as {@code annual_revenue IS NULL}, so the missing
 *       nine-tenths are something a consultant can count and choose, rather than a silent exclusion.
 * </ul>
 *
 * <p><b>Facet counts are taken over the whole universe, not over the current selection.</b> That
 * matches the mockup, and it is the more useful reading: a chip that answered "how many are left"
 * would keep changing under the hand that is trying to decide how big a slice of the market it
 * represents. It also means the five accordions are one cacheable read that no filter invalidates.
 *
 * <p><b>Every read below is cached, and the reason they may be is worth stating rather than
 * assuming.</b> The application's tenant-isolation rule is that a workspace-scoped query filters by
 * {@code AuthPrincipal.requireWorkspaceId()} — and these caches are shared across every workspace in
 * the process, so that rule has to be met differently here:
 *
 * <ul>
 *   <li><b>No read here takes a workspace, a project or a user.</b> Each is a pure function of its
 *       arguments over reference data. Two mandates in two different firms with the same filter share
 *       an entry and both get the same correct answer, because they were asking the same question of
 *       the same table.
 *   <li><b>A caller cannot forge a key.</b> {@link CompanyScope} is resolved server-side from the
 *       mandate's stored filter by {@code StrategyScope}, never from a request parameter.
 *   <li><b>There is no oracle.</b> Producing a key means already being able to run the query, and the
 *       answer is universe data the caller holds {@code PROJECT_BROWSE} or {@code WORK_VIEW} to read.
 * </ul>
 *
 * <p><b>The guard-rail:</b> all of that holds only while the arguments fully determine the result. A
 * filter axis drawing on mandate-specific data — "exclude companies already triaged into <i>this</i>
 * project" is the obvious next one, and {@code triagecompany} sits right beside this — would make the
 * scope an incomplete key, and every row served from {@code companyScopeCount} /
 * {@code companyScopePage} would then be one mandate's answer handed to another. Adding such an axis
 * means re-keying these caches or dropping them, not just editing {@code buildWhere}.
 *
 * <p>Staleness is bounded twice: a TTL per cache, and {@link UniverseReloadWatch}, which notices a
 * pipeline reload and clears the lot.
 */
@Service
@RequiredArgsConstructor
public class ApolloCompanyQueryService {

    /** Every column the list and the write-path snapshots need, in one place so they cannot drift. */
    private static final String ROW_COLUMNS = """
            apollo_account_id, company_name, industry, company_country, company_city,
            num_employees, annual_revenue, website, logo_url,
            short_description, founded_year""";

    private final JdbcClient jdbc;
    private final SectorTaxonomy taxonomy;
    private final MarketSegments marketSegments;

    // Every cached read below returns an immutable list, and that is a requirement rather than a
    // preference. A cache hands the same instance to every caller, so one that sorted or added to
    // what it got back would rewrite the answer every later request receives — silently, for a whole
    // TTL, and never reproducibly. Immutable turns that into an exception at the mutation.

    /**
     * How many companies the scope matches. An empty scope is the whole universe, not nothing.
     *
     * <p>The most valuable entry in any of these caches. The Strategy table asks for this total
     * alongside every page it draws, so it is recomputed on every page turn and every sort change of
     * a filter that has not moved — and unlike a page of rows, the value is one {@code Long}.
     */
    @Cacheable(CacheConfig.COMPANY_SCOPE_COUNT)
    public long count(CompanyScope scope) {
        WhereClause where = buildWhere(scope);
        return bind(jdbc.sql("SELECT count(*) FROM app_lm_apollo_companies WHERE " + where.sql()),
                where.params()).query(Long.class).single();
    }

    /**
     * One page of the scope, sorted by a column from {@link CompanySortField}'s allowlist. The caller
     * supplies the page, the size and the sort; the scope itself is resolved server-side from the
     * mandate's saved filter and never from a request parameter.
     *
     * <p>All five arguments are the key, because all five change the answer. The heaviest entries the
     * application holds — up to {@code max-page-size} rows apiece — which is why their cache is the
     * most tightly bounded.
     */
    @Cacheable(CacheConfig.COMPANY_SCOPE_PAGE)
    public List<CompanyRow> search(CompanyScope scope, CompanySortField sort, SortDirection direction,
                                   int page, int size) {
        WhereClause where = buildWhere(scope);
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        String sql = """
                SELECT %s
                FROM app_lm_apollo_companies
                WHERE %s
                ORDER BY %s, apollo_account_id
                LIMIT :size OFFSET :offset
                """.formatted(ROW_COLUMNS, where.sql(), sort.orderByTerms(direction));
        params.put("size", size);
        params.put("offset", (long) page * size);
        return List.copyOf(bind(jdbc.sql(sql), params).query(COMPANY_ROW_MAPPER).list());
    }

    /**
     * The named companies, whatever the scope. This is the write path's seam: the off-limits list, the
     * project universe and the client registry all store a snapshot of a company at the moment it was
     * picked, and they resolve it here so only a company the universe actually holds can be stored.
     */
    public List<CompanyRow> byAccountIds(List<String> apolloAccountIds) {
        if (apolloAccountIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT %s
                        FROM app_lm_apollo_companies
                        WHERE apollo_account_id IN (:ids)
                        """.formatted(ROW_COLUMNS))
                .param("ids", apolloAccountIds)
                .query(COMPANY_ROW_MAPPER)
                .list();
    }

    /**
     * Name-prefix search for the company pickers — the off-limits list and the client registry. Ranked
     * so a prefix match beats a match buried mid-name, then by size, because the company a consultant
     * means when they type three letters is almost always the biggest one that starts with them.
     *
     * <p>The cache key lower-cases the query, which costs nothing in correctness and doubles the hit
     * rate: both matches are {@code ILIKE}, so "SAU", "Sau" and "sau" return byte-identical rows, and
     * a picker is exactly where people type the same three letters three different ways. {@code
     * Locale.ROOT} rather than the default locale — under a Turkish default, {@code "I"} lower-cases
     * to a dotless {@code ı} and the same query would key to two entries on two machines.
     *
     * <p>The key is a SpEL list rather than a concatenated string so that it cannot collide: no
     * choice of separator is safe when one half is arbitrary user text.
     *
     * <p>A <b>miss</b> still costs a full scan of the universe: {@code company_name} carries no index
     * and a leading-wildcard {@code ILIKE} could not use one anyway. User-typed text is an unbounded
     * key space, so the tail always misses, and the durable fix for that is a {@code pg_trgm} index —
     * an ops script, since this table is owned by {@code postgres} post-harden, not a migration.
     */
    @Cacheable(cacheNames = CacheConfig.COMPANY_TYPEAHEAD,
            key = "{#limit, #query.toLowerCase(T(java.util.Locale).ROOT)}")
    public List<CompanyRow> typeahead(String query, int limit) {
        String pattern = escapeLikePattern(query);
        return jdbc.sql("""
                        SELECT %s
                        FROM app_lm_apollo_companies
                        WHERE company_name ILIKE :contains ESCAPE '\\'
                        ORDER BY (company_name ILIKE :prefix ESCAPE '\\') DESC,
                                 num_employees DESC NULLS LAST,
                                 company_name
                        LIMIT :limit
                        """.formatted(ROW_COLUMNS))
                .param("contains", "%" + pattern + "%")
                .param("prefix", pattern + "%")
                .param("limit", limit)
                .query(COMPANY_ROW_MAPPER)
                .list()
                .stream()
                .toList();
    }

    /**
     * The Industry accordion: the universe's industries with their counts, arranged into the
     * taxonomy's groups. Groups keep the taxonomy's file order rather than sorting by size — the
     * sidebar's order should not rearrange itself when the pipeline reloads — while the industries
     * inside a group are ranked most populous first, which is what makes a long group scannable.
     *
     * <p>An industry the taxonomy does not cover is dropped here, which would hide it from the
     * sidebar entirely. {@code SectorTaxonomyCoverageIntegrationTest} asserts that set is empty
     * against the real table, so this is a guarded impossibility rather than a silent loss.
     */
    @Cacheable(cacheNames = CacheConfig.COMPANY_FACETS, key = "'sectorGroups'")
    public List<SectorGroup> sectorGroups() {
        Map<String, Long> countByIndustry = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT industry AS label, count(*) AS count
                        FROM app_lm_apollo_companies
                        WHERE industry IS NOT NULL AND industry <> ''
                        GROUP BY 1
                        """)
                .query(ScopeBreakdown.class)
                .list()
                .forEach(row -> countByIndustry.put(row.label(), row.count()));

        List<SectorGroup> groups = new ArrayList<>();
        taxonomy.groups().forEach((groupName, industries) -> {
            List<FacetCount> counted = industries.stream()
                    .map(industry -> new FacetCount(industry, industry,
                            countByIndustry.getOrDefault(industry, 0L)))
                    .sorted(Comparator.comparingLong(FacetCount::count).reversed()
                            .thenComparing(FacetCount::label))
                    .toList();
            groups.add(new SectorGroup(groupName,
                    counted.stream().mapToLong(FacetCount::count).sum(), counted));
        });
        return List.copyOf(groups);
    }

    /**
     * The Market Segments accordion: how many companies each segment's keywords reach.
     *
     * <p>One query per segment rather than one GROUP BY, because segments <b>overlap</b> — a company
     * can be B2B and SaaS and Fintech at once, and a single grouped count would have to pick one and
     * silently under-report the rest. Eleven cheap index probes buy a set of counts that add up to
     * more than the universe, which is the honest answer for an axis where a company can hold several
     * positions.
     *
     * <p>Segments keep the file's order, not size order: this is a short fixed list the eye learns,
     * and re-ranking it on every pipeline load would move the chip out from under the hand.
     */
    @Cacheable(cacheNames = CacheConfig.COMPANY_FACETS, key = "'marketSegmentFacets'")
    public List<FacetCount> marketSegmentFacets() {
        List<FacetCount> facets = new ArrayList<>();
        marketSegments.segments().forEach((segment, keywords) -> {
            Map<String, Object> params = new LinkedHashMap<>();
            String sql = """
                    SELECT count(*)
                    FROM app_lm_apollo_companies
                    WHERE keywords && %s
                    """.formatted(arrayLiteral(keywords, "segKw", params));
            long count = bind(jdbc.sql(sql), params).query(Long.class).single();
            facets.add(new FacetCount(segment, segment, count));
        });
        return List.copyOf(facets);
    }

    /**
     * The Location accordion. Ranked by size, and the live vocabulary is the six GCC countries — which
     * is why the mockup's six fixed chips turned out to be the whole list rather than a sample.
     */
    @Cacheable(cacheNames = CacheConfig.COMPANY_FACETS, key = "'countryFacets'")
    public List<FacetCount> countryFacets() {
        return jdbc.sql("""
                        SELECT company_country AS label, count(*) AS count
                        FROM app_lm_apollo_companies
                        WHERE company_country IS NOT NULL AND company_country <> ''
                        GROUP BY 1
                        ORDER BY count(*) DESC, 1
                        """)
                .query(ScopeBreakdown.class)
                .list()
                .stream()
                .map(row -> new FacetCount(row.label(), row.label(), row.count()))
                .toList();
    }

    /**
     * The two size accordions. One GROUP BY over a CASE built from the enum's own bounds, so the
     * chip counts and the filter that runs when the chip is clicked can never disagree — a band
     * counted by one set of numbers and filtered by another is the bug this shape prevents.
     *
     * <p>Bands are returned in enum order, including any that count zero: a band silently missing
     * from the sidebar reads as "no such size", where a zero reads as "none in this market".
     */
    @Cacheable(cacheNames = CacheConfig.COMPANY_FACETS, key = "'employeeBandFacets'")
    public List<FacetCount> employeeBandFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = bandCounts(employeeBandCase(params), params);
        return Arrays.stream(EmployeeBand.values())
                .map(band -> new FacetCount(band.value(), band.label(),
                        counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    /** The Revenue accordion, Unknown included — see {@link RevenueBand#R_UNKNOWN}. */
    @Cacheable(cacheNames = CacheConfig.COMPANY_FACETS, key = "'revenueBandFacets'")
    public List<FacetCount> revenueBandFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = bandCounts(revenueBandCase(params), params);
        return Arrays.stream(RevenueBand.values())
                .map(band -> new FacetCount(band.value(), band.label(),
                        counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    /** The scope's most populous industries, largest first — a report aggregate. */
    public List<ScopeBreakdown> countBySector(CompanyScope scope, int limit) {
        return breakdown(scope, "industry", "industry IS NOT NULL AND industry <> ''", limit);
    }

    /** The scope's most populous countries, largest first — a report aggregate. */
    public List<ScopeBreakdown> countByCountry(CompanyScope scope, int limit) {
        return breakdown(scope, "company_country",
                "company_country IS NOT NULL AND company_country <> ''", limit);
    }

    /** The scope's most populous cities, largest first — a report aggregate. */
    public List<ScopeBreakdown> countByCity(CompanyScope scope, int limit) {
        return breakdown(scope, "company_city",
                "company_city IS NOT NULL AND company_city <> ''", limit);
    }

    /**
     * The shared shape behind every grouped aggregate: the scope's WHERE clause, grouped by one
     * column. {@code presenceCondition} drops rows the grouping column is missing on, since a bar
     * labelled with a blank is noise rather than a finding.
     */
    private List<ScopeBreakdown> breakdown(CompanyScope scope, String column, String presenceCondition,
                                           int limit) {
        WhereClause where = buildWhere(scope);
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        params.put("groupLimit", limit);
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies
                WHERE %s AND %s
                GROUP BY 1
                ORDER BY count(*) DESC, 1
                LIMIT :groupLimit
                """.formatted(column, where.sql(), presenceCondition);
        return bind(jdbc.sql(sql), params).query(ScopeBreakdown.class).list();
    }

    /**
     * Every criterion the sidebar can set, ANDed. Each is omitted entirely when it selects nothing,
     * so an untouched filter renders as {@code TRUE} and returns the whole universe — the right
     * opening state for a search screen, and the opposite of the criteria model this replaced, which
     * refused to answer until a sector was chosen.
     */
    private WhereClause buildWhere(CompanyScope scope) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();

        if (!scope.industries().isEmpty()) {
            // Lower-cased on both sides. Apollo's vocabulary is already lower-case throughout, but a
            // filter saved from a facet response should not depend on that staying true.
            clauses.add("lower(industry) IN (:industries)");
            params.put("industries", lowered(scope.industries()));
        }
        List<String> segmentKeywords = marketSegments.keywordsOfAll(scope.marketSegments());
        if (!segmentKeywords.isEmpty()) {
            clauses.add("keywords && " + arrayLiteral(segmentKeywords, "segKw", params));
        }
        if (!scope.countries().isEmpty()) {
            clauses.add("company_country IN (:countries)");
            params.put("countries", scope.countries());
        }
        // A custom range and the predefined rows are the panel's two modes, so the range wins outright
        // rather than intersecting: a consultant who typed 250-400 means that, not "and also 201-500".
        String employeeClause = scope.employeeRange() != null
                ? rangeClause("num_employees", scope.employeeRange(), "empRange", params)
                : employeeBandClause(scope.employeeBands(), params);
        if (employeeClause != null) {
            clauses.add(employeeClause);
        }
        String revenueClause = scope.revenueRange() != null
                ? rangeClause("annual_revenue", scope.revenueRange(), "revRange", params)
                : revenueBandClause(scope.revenueBands(), params);
        if (revenueClause != null) {
            clauses.add(revenueClause);
        }
        if (!scope.offLimitsAccountIds().isEmpty()) {
            clauses.add("apollo_account_id NOT IN (:offLimitsIds)");
            params.put("offLimitsIds", scope.offLimitsAccountIds());
        }
        if (scope.nameQuery() != null) {
            clauses.add("company_name ILIKE :nameQuery ESCAPE '\\'");
            params.put("nameQuery", "%" + escapeLikePattern(scope.nameQuery()) + "%");
        }
        return new WhereClause(clauses.isEmpty() ? "TRUE" : String.join(" AND ", clauses), params);
    }

    /**
     * A typed custom range over one column. Either end may be absent — "at least 500" and "up to 500"
     * are both things a half-filled pair of inputs legitimately means — and a range with neither end
     * never reaches here, because {@code StrategyFilter} normalises it away.
     */
    private static String rangeClause(String column, NumericRange range, String prefix,
                                      Map<String, Object> params) {
        List<String> bounds = new ArrayList<>(2);
        if (range.min() != null) {
            params.put(prefix + "Min", range.min());
            bounds.add("%s >= :%sMin".formatted(column, prefix));
        }
        if (range.max() != null) {
            params.put(prefix + "Max", range.max());
            bounds.add("%s <= :%sMax".formatted(column, prefix));
        }
        return bounds.isEmpty() ? null : "(" + String.join(" AND ", bounds) + ")";
    }

    /** Selected headcount bands as an OR of closed numeric ranges. */
    private static String employeeBandClause(List<String> bandValues, Map<String, Object> params) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        for (String bandValue : bandValues) {
            EmployeeBand band = EmployeeBand.fromValue(bandValue);
            if (band == null) {
                continue;
            }
            ranges.add(boundsClause("num_employees", band.lowerBound(), band.upperBound(),
                    "emp", index++, params));
        }
        return ranges.isEmpty() ? null : "(" + String.join(" OR ", ranges) + ")";
    }

    /**
     * Selected revenue bands as an OR of numeric ranges, with Unknown joining as a null test. A row
     * with no figure is excluded from every *numeric* band — it cannot be shown to fall in one — so
     * Unknown is how those 64,690 companies are reached at all.
     */
    private static String revenueBandClause(List<String> bandValues, Map<String, Object> params) {
        List<String> ranges = new ArrayList<>();
        int index = 0;
        for (String bandValue : bandValues) {
            RevenueBand band = RevenueBand.fromValue(bandValue);
            if (band == null) {
                continue;
            }
            if (band.isUnknown()) {
                ranges.add("annual_revenue IS NULL");
                continue;
            }
            ranges.add(boundsClause("annual_revenue", band.lowerBound(), band.upperBound(),
                    "rev", index++, params));
        }
        return ranges.isEmpty() ? null : "(" + String.join(" OR ", ranges) + ")";
    }

    /** One band's range over one column: BETWEEN when bounded, {@code >=} for an open-ended top band. */
    private static String boundsClause(String column, long lowerBound, Long upperBound, String prefix,
                                       int index, Map<String, Object> params) {
        String lowParam = prefix + "Low" + index;
        params.put(lowParam, lowerBound);
        if (upperBound == null) {
            return "%s >= :%s".formatted(column, lowParam);
        }
        String highParam = prefix + "High" + index;
        params.put(highParam, upperBound);
        return "%s BETWEEN :%s AND :%s".formatted(column, lowParam, highParam);
    }

    /** A CASE mapping each headcount to its band's wire token, built from the enum's own bounds. */
    private static String employeeBandCase(Map<String, Object> params) {
        StringBuilder expression = new StringBuilder("CASE");
        int index = 0;
        for (EmployeeBand band : EmployeeBand.values()) {
            expression.append(" WHEN ")
                    .append(boundsClause("num_employees", band.lowerBound(), band.upperBound(),
                            "empCase", index, params))
                    .append(" THEN :empLabel").append(index);
            params.put("empLabel" + index, band.value());
            index++;
        }
        return expression.append(" END").toString();
    }

    /** The same for revenue, with the null case first so it wins before any range is considered. */
    private static String revenueBandCase(Map<String, Object> params) {
        StringBuilder expression = new StringBuilder("CASE WHEN annual_revenue IS NULL THEN :revUnknown");
        params.put("revUnknown", RevenueBand.R_UNKNOWN.value());
        int index = 0;
        for (RevenueBand band : RevenueBand.values()) {
            if (band.isUnknown()) {
                continue;
            }
            expression.append(" WHEN ")
                    .append(boundsClause("annual_revenue", band.lowerBound(), band.upperBound(),
                            "revCase", index, params))
                    .append(" THEN :revLabel").append(index);
            params.put("revLabel" + index, band.value());
            index++;
        }
        return expression.append(" END").toString();
    }

    /** Counts per band token for a CASE expression, over the whole universe. */
    private Map<String, Long> bandCounts(String bandExpression, Map<String, Object> params) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String sql = """
                SELECT %s AS label, count(*) AS count
                FROM app_lm_apollo_companies
                GROUP BY 1
                """.formatted(bandExpression);
        bind(jdbc.sql(sql), params).query(ScopeBreakdown.class).list()
                .forEach(row -> counts.put(row.label(), row.count()));
        return counts;
    }

    /**
     * Hand-written rather than the reflective mapper: the nullable numerics have to come back as boxed
     * types so a missing revenue reads as absent rather than as zero.
     *
     * <p>{@code founded_year} goes through {@link Number} rather than a direct cast. It is a
     * {@code smallint}, and {@code getObject} on one returns an {@code Integer} from this driver —
     * a {@code (Short)} cast compiles, reads correctly, and then throws ClassCastException on the
     * first row that actually carries a year, which is a 500 on the list rather than a wrong value.
     */
    private static final RowMapper<CompanyRow> COMPANY_ROW_MAPPER = ApolloCompanyQueryService::mapRow;

    private static CompanyRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new CompanyRow(
                rs.getString("apollo_account_id"),
                rs.getString("company_name"),
                rs.getString("industry"),
                rs.getString("company_country"),
                rs.getString("company_city"),
                (Integer) rs.getObject("num_employees"),
                (Long) rs.getObject("annual_revenue"),
                rs.getString("website"),
                rs.getString("logo_url"),
                rs.getString("short_description"),
                intOrNull((Number) rs.getObject("founded_year")));
    }

    /**
     * A Postgres array built from one bound parameter per element — {@code ARRAY[:p0, :p1]}.
     *
     * <p>Not a single {@code String[]} parameter, which is the obvious way and is wrong here: Spring's
     * named-parameter expansion turns any array or collection into a comma-separated list of
     * placeholders, which is what makes {@code IN (:values)} work and what would render this as
     * {@code keywords && ?, ?, ?}. Building the literal keeps every value bound while still producing
     * an array the {@code &&} operator can use against {@code idx_lm_apollo_kw}.
     *
     * <p>The {@code ::text[]} cast is not decoration. The driver binds a String parameter as
     * {@code varchar}, so the literal comes out as {@code character varying[]} and Postgres refuses
     * {@code text[] && character varying[]} — "operator does not exist", a 500 rather than a wrong
     * answer. Casting the whole array once also keeps the operand a plain {@code text[]}, which is
     * what {@code idx_lm_apollo_kw} is built on.
     *
     * <p>Array overlap rather than an unnest-and-lower comparison: every keyword in the table is
     * already lower-case, so this form can use the GIN index where the safer-looking one cannot.
     */
    private static String arrayLiteral(List<String> values, String prefix, Map<String, Object> params) {
        List<String> placeholders = new ArrayList<>(values.size());
        int index = 0;
        for (String value : values) {
            String name = prefix + index++;
            params.put(name, value);
            placeholders.add(":" + name);
        }
        return "ARRAY[" + String.join(", ", placeholders) + "]::text[]";
    }

    /** Widen whatever numeric type the driver chose, or keep the absence. */
    private static Integer intOrNull(Number value) {
        return value == null ? null : value.intValue();
    }

    private static List<String> lowered(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
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
