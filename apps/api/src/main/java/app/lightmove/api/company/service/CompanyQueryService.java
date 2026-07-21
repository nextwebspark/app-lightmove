package app.lightmove.api.company.service;

import app.lightmove.api.company.constant.CompanySearchOrder;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchResult;
import app.lightmove.api.company.dto.CompanyDtos.SectorCount;
import app.lightmove.api.company.dto.CompanyDtos.SuggestionsResponse;
import app.lightmove.api.company.dto.CompanyDtos.TagCount;
import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.model.CompanyRefRow;
import app.lightmove.api.core.config.LightMoveProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read-only queries over the company universe (app_lm_companies). The table is ETL-owned reference
 * data whose every useful read here is an aggregate or a filtered projection over a scalar sector, a
 * {@code text[]} of tags, or a numeric size column, so there is no JPA entity — a {@link JdbcClient}
 * answers each question directly.
 *
 * <p>The universe is shared, not workspace-scoped: any project browser reads the same ~54k companies.
 * Tenant isolation happens at the strategy that <i>stores</i> a selection, not here. This service stays
 * agnostic of the {@code project} feature's {@code EmployeeBand}/{@code RevenueBand} enums — callers
 * resolve a band to its numeric bounds and pass a plain {@link Range}, so this class never needs to know
 * where the bounds came from.
 *
 * <p>{@link #refsByKeys} is a deliberate seam for the strategy write path: the target/off-limits
 * lists resolve their snapshots here at save time, so the client can only ever store companies the
 * universe actually holds.
 */
@Service
public class CompanyQueryService {

    private final JdbcClient jdbc;
    private final SectorAdjacency adjacency;
    private final LightMoveProperties.Company.Suggestions suggestionsConfig;

    public CompanyQueryService(JdbcClient jdbc, SectorAdjacency adjacency,
                               LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.adjacency = adjacency;
        this.suggestionsConfig = properties.company().suggestions();
    }

    /**
     * A numeric size bound, in whichever unit the caller's column uses. Employee headcount bounds are
     * inclusive {@code [min, max]}; revenue bounds are half-open {@code [min, max)} — this type doesn't
     * know which, so {@link #buildWhere} takes that as a separate flag per axis. A {@code null} bound is
     * open-ended.
     */
    public record Range(Long min, Long max) {}

    /** One row of the company universe, as read back for a filtered list. */
    public record CompanyRow(long id, String name, String domain, String primaryIndustry,
                              String hqCountry, String hqCity, String employeeRange,
                              String revenueRange, String matchTier) {}

    /** Every sector (primary_industry) with its company count, most populous first. */
    public List<SectorCount> sectors() {
        return jdbc.sql("""
                        SELECT primary_industry AS name, count(*) AS count
                        FROM app_lm_companies
                        WHERE primary_industry IS NOT NULL
                        GROUP BY primary_industry
                        ORDER BY count(*) DESC, primary_industry
                        """)
                .query(SectorCount.class)
                .list();
    }

    /**
     * Suggestions for a set of chosen direct sectors: adjacent sectors from the curated map, and
     * inferred tags computed live from the tags that co-occur with those sectors — minus any tag that
     * merely restates ground the direct or adjacent sectors already cover.
     */
    public SuggestionsResponse suggestionsFor(List<String> sectors) {
        if (sectors.isEmpty()) {
            return new SuggestionsResponse(List.of(), List.of());
        }
        List<String> adjacent = adjacency.suggestFor(sectors, suggestionsConfig.adjacentSectorLimit());

        Set<String> coveredGround = new LinkedHashSet<>();
        sectors.forEach(s -> coveredGround.add(s.toLowerCase(Locale.ROOT)));
        adjacent.forEach(a -> coveredGround.add(a.toLowerCase(Locale.ROOT)));

        List<TagCount> inferred = coOccurringTags(sectors).stream()
                .filter(tag -> !coveredGround.contains(tag.tag().toLowerCase(Locale.ROOT)))
                .limit(suggestionsConfig.inferredTagLimit())
                .toList();
        return new SuggestionsResponse(adjacent, inferred);
    }

    private List<TagCount> coOccurringTags(List<String> sectors) {
        return jdbc.sql("""
                        SELECT tag, count(*) AS count
                        FROM app_lm_companies, unnest(industry_tags) AS tag
                        WHERE primary_industry IN (:sectors)
                        GROUP BY tag
                        ORDER BY count(*) DESC, tag
                        LIMIT :fetch
                        """)
                .param("sectors", sectors)
                .param("fetch", suggestionsConfig.inferredTagFetchSize())
                .query(TagCount.class)
                .list();
    }

    /**
     * How many companies match the current scope: those in any selected sector or carrying any selected
     * tag, narrowed further by the employee and/or revenue bands when given. An empty scope (no sectors,
     * no tags) matches nothing without touching the database.
     */
    public long estimate(List<String> sectors, List<String> tags, List<Range> employeeRanges,
                          List<Range> revenueRanges) {
        WhereClause where = buildWhere(sectors, tags, employeeRanges, revenueRanges);
        if (where == null) {
            return 0;
        }
        StatementParams count = bind(jdbc.sql("SELECT count(*) FROM app_lm_companies WHERE " + where.sql()),
                where.params());
        return count.spec().query(Long.class).single();
    }

    /**
     * The companies matching the scope, one page at a time, ordered by name for a stable page boundary.
     * An empty scope returns an empty page without touching the database, mirroring {@link #estimate}.
     * {@code directSectors} and {@code adjacentSectors} are kept separate (unlike {@link #estimate}'s
     * single combined list) purely so each row can report which bucket it matched through.
     */
    public List<CompanyRow> search(List<String> directSectors, List<String> adjacentSectors,
                                    List<String> tags, List<Range> employeeRanges,
                                    List<Range> revenueRanges, int page, int size) {
        List<String> allSectors = new ArrayList<>(directSectors);
        allSectors.addAll(adjacentSectors);
        WhereClause where = buildWhere(allSectors, tags, employeeRanges, revenueRanges);
        if (where == null) {
            return List.of();
        }
        Map<String, Object> params = new LinkedHashMap<>(where.params());
        String matchTier = matchTierExpression(directSectors, adjacentSectors, params);
        String sql = """
                SELECT id, name, domain, primary_industry, hq_country, hq_city, employee_range, revenue_range,
                       %s AS match_tier
                FROM app_lm_companies
                WHERE %s
                ORDER BY name
                LIMIT :limit OFFSET :offset
                """.formatted(matchTier, where.sql());
        StatementParams spec = bind(jdbc.sql(sql), params);
        return spec.spec()
                .param("limit", size)
                .param("offset", page * size)
                .query(CompanyRow.class)
                .list();
    }

    /**
     * Which bucket a row matched through: a direct sector, an adjacent sector, or (by elimination) an
     * inferred tag. Safe to fall through to {@code 'INFERRED'} unconditionally — the WHERE clause already
     * guarantees every row matched on sector-or-tag, so anything neither list catches must be a tag match.
     * Each {@code WHEN} is only added when its list is non-empty, matching {@link #buildWhere}'s own
     * "never hand an empty list to {@code IN}" convention.
     */
    private static String matchTierExpression(List<String> directSectors, List<String> adjacentSectors,
                                                Map<String, Object> params) {
        StringBuilder expr = new StringBuilder("CASE");
        if (!directSectors.isEmpty()) {
            expr.append(" WHEN primary_industry IN (:directSectors) THEN 'DIRECT'");
            params.put("directSectors", directSectors);
        }
        if (!adjacentSectors.isEmpty()) {
            expr.append(" WHEN primary_industry IN (:adjacentSectors) THEN 'ADJACENT'");
            params.put("adjacentSectors", adjacentSectors);
        }
        expr.append(" ELSE 'INFERRED' END");
        return expr.toString();
    }

    private record WhereClause(String sql, Map<String, Object> params) {}

    private record StatementParams(JdbcClient.StatementSpec spec) {}

    /**
     * The shared filter behind every scoped read: sector/tag match (OR'd together), AND'd with an
     * employee-band match (bands OR'd within the axis) AND a revenue-band match, each axis omitted
     * entirely when its band list is empty. Returns {@code null} when there is no sector/tag scope at
     * all — an intentionally unfiltered size-only query isn't a supported scope, matching the Strategy
     * screen's own "zero until a sector or tag is picked" convention.
     */
    private WhereClause buildWhere(List<String> sectors, List<String> tags, List<Range> employeeRanges,
                                    List<Range> revenueRanges) {
        if (sectors.isEmpty() && tags.isEmpty()) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();

        List<String> scopeParts = new ArrayList<>();
        if (!sectors.isEmpty()) {
            scopeParts.add("primary_industry IN (:sectors)");
            params.put("sectors", sectors);
        }
        if (!tags.isEmpty()) {
            scopeParts.add("industry_tags && ARRAY[:tags]::text[]");
            params.put("tags", tags);
        }
        clauses.add("(" + String.join(" OR ", scopeParts) + ")");

        if (!employeeRanges.isEmpty()) {
            clauses.add(axisClause("employee_count", employeeRanges, params, "e", true));
        }
        if (!revenueRanges.isEmpty()) {
            clauses.add(axisClause("revenue_usd", revenueRanges, params, "r", false));
        }

        return new WhereClause(String.join(" AND ", clauses), params);
    }

    /**
     * One axis's OR'd set of band ranges, each rendered as its own bound pair so an open-ended band
     * (a {@code null} min or max) simply omits that side. {@code inclusiveUpper} follows each band
     * enum's own convention — headcount is {@code [min, max]}, revenue is {@code [min, max)}.
     */
    private static String axisClause(String column, List<Range> ranges, Map<String, Object> params,
                                      String prefix, boolean inclusiveUpper) {
        List<String> parts = new ArrayList<>();
        int index = 0;
        for (Range range : ranges) {
            List<String> bounds = new ArrayList<>();
            if (range.min() != null) {
                String key = prefix + "Min" + index;
                bounds.add(column + " >= :" + key);
                params.put(key, range.min());
            }
            if (range.max() != null) {
                String key = prefix + "Max" + index;
                bounds.add(column + (inclusiveUpper ? " <= :" : " < :") + key);
                params.put(key, range.max());
            }
            parts.add(bounds.isEmpty() ? "TRUE" : "(" + String.join(" AND ", bounds) + ")");
            index++;
        }
        return "(" + String.join(" OR ", parts) + ")";
    }

    private static StatementParams bind(JdbcClient.StatementSpec spec, Map<String, Object> params) {
        JdbcClient.StatementSpec bound = spec;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            bound = bound.param(entry.getKey(), entry.getValue());
        }
        return new StatementParams(bound);
    }

    /** Every column the picker shows and stores for one company row. */
    private static final String PICKER_COLUMNS = """
            SELECT source, source_id, name, domain, slogan, logo,
                   primary_industry, hq_city, hq_country, employee_count
            FROM app_lm_companies
            """;

    /** The zero-query browse: companies by revenue, optionally narrowed to the given sectors. */
    public List<CompanySearchResult> browse(List<String> sectors, CompanySearchOrder order, int limit) {
        StringBuilder sql = new StringBuilder(PICKER_COLUMNS);
        List<String> conditions = new ArrayList<>();
        if (order == CompanySearchOrder.REVENUE_ASC) {
            // Ascending shows only companies with a known figure; nulls would otherwise lead the list.
            conditions.add("revenue_usd IS NOT NULL");
        }
        if (!sectors.isEmpty()) {
            conditions.add("primary_industry IN (:sectors)");
        }
        if (!conditions.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", conditions)).append("\n");
        }
        sql.append(order == CompanySearchOrder.REVENUE_ASC
                ? "ORDER BY revenue_usd ASC, name\n"
                : "ORDER BY revenue_usd DESC NULLS LAST, name\n");
        sql.append("LIMIT :limit");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("limit", limit);
        if (!sectors.isEmpty()) {
            spec = spec.param("sectors", sectors);
        }
        return spec.query(CompanySearchResult.class).list();
    }

    /**
     * Companies whose name contains the query, case-insensitively — the picker typeahead. Prefix
     * matches rank first, then the larger company wins (a short query should surface the household
     * name, not an obscure exact match). The query is escaped so {@code %} and {@code _} match
     * literally. No index backs this: a LIMITed scan over ~54k rows is tens of milliseconds, and
     * app_lm_companies is owned by postgres post-harden, so an index would be an ops script, not a
     * migration.
     */
    public List<CompanySearchResult> search(String query, int limit) {
        String escaped = escapeLikePattern(query);
        return jdbc.sql(PICKER_COLUMNS + """
                        WHERE name ILIKE :contains ESCAPE '\\'
                        ORDER BY (name ILIKE :prefix ESCAPE '\\') DESC,
                                 employee_count DESC NULLS LAST, name
                        LIMIT :limit
                        """)
                .param("contains", "%" + escaped + "%")
                .param("prefix", escaped + "%")
                .param("limit", limit)
                .query(CompanySearchResult.class)
                .list();
    }

    /**
     * Resolve rebuild-stable {@code (source, source_id)} keys to their current snapshot rows. Keys
     * the universe no longer holds are simply absent from the result — the caller decides whether
     * that is an error (a new selection) or expected (a stored entry whose company vanished upstream).
     */
    public List<CompanyRefRow> refsByKeys(List<CompanyKey> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> sources = keys.stream().map(CompanyKey::source).toList();
        List<String> sourceIds = keys.stream().map(CompanyKey::sourceId).toList();
        return jdbc.sql("""
                        SELECT source, source_id, name, domain, slogan, logo, hq_city, hq_country
                        FROM app_lm_companies
                        WHERE (source, source_id) IN
                              (SELECT * FROM unnest(ARRAY[:sources]::text[], ARRAY[:sourceIds]::text[]))
                        """)
                .param("sources", sources)
                .param("sourceIds", sourceIds)
                .query(CompanyRefRow.class)
                .list();
    }

    /** Backslash-escape LIKE's wildcards so the user's text matches literally. */
    private static String escapeLikePattern(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
