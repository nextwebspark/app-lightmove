package app.lightmove.api.company.service;

import app.lightmove.api.company.constant.CompanySearchOrder;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchResult;
import app.lightmove.api.company.dto.CompanyDtos.SectorCount;
import app.lightmove.api.company.dto.CompanyDtos.SuggestionsResponse;
import app.lightmove.api.company.dto.CompanyDtos.TagCount;
import app.lightmove.api.company.model.CompanyKey;
import app.lightmove.api.company.model.CompanyRefRow;
import app.lightmove.api.core.config.LightMoveProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read-only queries over the company universe (app_lm_companies). The table is ETL-owned reference
 * data whose every useful read here is an aggregate over a scalar sector or a {@code text[]} of tags,
 * so there is no JPA entity — a {@link JdbcClient} answers each question directly.
 *
 * <p>The universe is shared, not workspace-scoped: any project browser reads the same ~54k companies.
 * Tenant isolation happens at the strategy that <i>stores</i> a selection, not here.
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
     * How many companies match the current scope: those in any selected sector, plus those carrying
     * any selected tag. Each side of the OR is omitted when its list is empty, and an empty scope
     * matches nothing without touching the database.
     */
    public long estimate(List<String> sectors, List<String> tags) {
        if (sectors.isEmpty() && tags.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM app_lm_companies WHERE ");
        JdbcClient.StatementSpec spec;
        if (!sectors.isEmpty() && !tags.isEmpty()) {
            sql.append("primary_industry IN (:sectors) OR industry_tags && ARRAY[:tags]::text[]");
            spec = jdbc.sql(sql.toString()).param("sectors", sectors).param("tags", tags);
        } else if (!sectors.isEmpty()) {
            sql.append("primary_industry IN (:sectors)");
            spec = jdbc.sql(sql.toString()).param("sectors", sectors);
        } else {
            sql.append("industry_tags && ARRAY[:tags]::text[]");
            spec = jdbc.sql(sql.toString()).param("tags", tags);
        }
        return spec.query(Long.class).single();
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
        // Ascending shows only companies with a known figure; nulls would otherwise lead the list.
        sql.append(order == CompanySearchOrder.REVENUE_ASC
                ? "WHERE revenue_usd IS NOT NULL\n"
                : "WHERE TRUE\n");
        if (!sectors.isEmpty()) {
            sql.append("AND primary_industry IN (:sectors)\n");
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
