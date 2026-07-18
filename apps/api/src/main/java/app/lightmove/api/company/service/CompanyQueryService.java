package app.lightmove.api.company.service;

import app.lightmove.api.company.dto.CompanyDtos.SectorCount;
import app.lightmove.api.company.dto.CompanyDtos.SuggestionsResponse;
import app.lightmove.api.company.dto.CompanyDtos.TagCount;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Read-only queries over the company universe (app_lm_companies). The table is ETL-owned reference
 * data whose every useful read here is an aggregate over a scalar sector or a {@code text[]} of tags,
 * so there is no JPA entity — a {@link JdbcClient} answers each question directly.
 *
 * <p>The universe is shared, not workspace-scoped: any project browser reads the same ~54k companies.
 * Tenant isolation happens at the strategy that <i>stores</i> a selection, not here.
 */
@Service
@RequiredArgsConstructor
public class CompanyQueryService {

    /** How many adjacent sectors the suggestion panel shows at most. */
    private static final int ADJACENT_LIMIT = 10;
    /** How many inferred tags survive to the response. */
    private static final int INFERRED_LIMIT = 8;
    /** How many co-occurring tags to pull before filtering out covered ground. */
    private static final int INFERRED_FETCH = 30;

    private final JdbcClient jdbc;
    private final SectorAdjacency adjacency;

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
        List<String> adjacent = adjacency.suggestFor(sectors, ADJACENT_LIMIT);

        Set<String> coveredGround = new LinkedHashSet<>();
        sectors.forEach(s -> coveredGround.add(s.toLowerCase(Locale.ROOT)));
        adjacent.forEach(a -> coveredGround.add(a.toLowerCase(Locale.ROOT)));

        List<TagCount> inferred = coOccurringTags(sectors).stream()
                .filter(tag -> !coveredGround.contains(tag.tag().toLowerCase(Locale.ROOT)))
                .limit(INFERRED_LIMIT)
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
                .param("fetch", INFERRED_FETCH)
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
}
