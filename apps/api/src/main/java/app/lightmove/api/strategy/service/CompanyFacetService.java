package app.lightmove.api.strategy.service;

import static app.lightmove.api.strategy.service.CompanyScopeSql.arrayLiteral;
import static app.lightmove.api.strategy.service.CompanyScopeSql.bind;
import static app.lightmove.api.strategy.service.CompanyScopeSql.employeeBandCase;
import static app.lightmove.api.strategy.service.CompanyScopeSql.escapeLikePattern;
import static app.lightmove.api.strategy.service.CompanyScopeSql.revenueBandCase;

import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.FacetAxis;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.dto.FacetCount;
import app.lightmove.api.strategy.dto.FacetCountsResponse;
import app.lightmove.api.strategy.dto.FacetValue;
import app.lightmove.api.strategy.dto.FacetsResponse;
import app.lightmove.api.strategy.dto.SectorGroup;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Everything the Strategy filter sidebar renders, over two reads that answer two different questions.
 *
 * <p>{@link #universeFacets()} is <b>the shape of the market</b>: which industries, countries, bands
 * and segments exist, what they are called, the order they render in, and how big each is across the
 * whole universe. It is the same for every mandate and changes only when the pipeline loads, so the
 * client caches it and no filter edit invalidates it.
 *
 * <p>{@link #countsFor(CompanyScope)} is <b>how one selection cuts that market</b>, and it is the
 * only number the sidebar actually shows once it has loaded. Each axis is counted with every
 * criterion applied except its own (see {@link FacetAxis}), so picking an industry recounts the bands
 * and segments under it while leaving the other industries countable.
 *
 * <p>Location is counted by neither. Its pills carry no number at all, and the country criterion
 * simply narrows every other axis.
 *
 * <p><b>Order comes from the universe read, never from the live counts.</b> Re-ranking a row by a
 * number that moves would slide the next chip out from under the hand that just clicked one — so the
 * counts change and the rows do not.
 */
@Service
@RequiredArgsConstructor
public class CompanyFacetService {

    private final JdbcClient jdbc;
    private final SectorTaxonomy taxonomy;
    private final MarketSegments marketSegments;
    private final IndustryAdjacency adjacency;

    /** Everything the five accordions can offer, counted over the whole universe. */
    public FacetsResponse universeFacets() {
        return new FacetsResponse(sectorGroups(), adjacency.neighbours(), marketSegmentFacets(),
                countryFacets(), employeeBandFacets(), revenueBandFacets());
    }

    /**
     * How many companies each option still reaches under this scope, keyed by the token a filter
     * stores.
     *
     * <p>One statement rather than five, so every axis is counted against one snapshot of the table
     * and the numbers cannot disagree with each other or with the results total beside them.
     *
     * <p><b>An option absent from a map counts zero.</b> The vocabularies live in
     * {@link #universeFacets()} and the client already renders from them, so repeating 148 industries
     * here to carry a zero would only give the two reads a way to hold different vocabularies.
     */
    public FacetCountsResponse countsFor(CompanyScope scope) {
        CompanyScopeSql predicate = CompanyScopeSql.of(scope, marketSegments);
        Map<String, Object> params = new LinkedHashMap<>(predicate.params());

        String sql = """
                SELECT '%s' AS axis, industry AS label, count(*) AS count
                  FROM app_lm_apollo_companies
                 WHERE %s AND industry IS NOT NULL AND industry <> ''
                 GROUP BY 2
                UNION ALL
                SELECT '%s', %s, count(*)
                  FROM app_lm_apollo_companies
                 WHERE %s
                 GROUP BY 2
                UNION ALL
                SELECT '%s', %s, count(*)
                  FROM app_lm_apollo_companies
                 WHERE %s
                 GROUP BY 2
                UNION ALL
                SELECT '%s', segment.segment_name, segment.segment_count
                  FROM %s
                """.formatted(
                FacetAxis.INDUSTRY.wireName(), predicate.excluding(FacetAxis.INDUSTRY).sql(),
                FacetAxis.EMPLOYEE_SIZE.wireName(), employeeBandCase(params),
                predicate.excluding(FacetAxis.EMPLOYEE_SIZE).sql(),
                FacetAxis.REVENUE.wireName(), revenueBandCase(params),
                predicate.excluding(FacetAxis.REVENUE).sql(),
                FacetAxis.MARKET_SEGMENT.wireName(),
                segmentTally(predicate.excluding(FacetAxis.MARKET_SEGMENT).sql(), params));

        List<AxisCount> rows = bind(jdbc.sql(sql), params)
                .query((rs, rowNumber) -> new AxisCount(rs.getString("axis"), rs.getString("label"),
                        rs.getLong("count")))
                .list();
        Map<String, Map<String, Long>> countsByAxis = new LinkedHashMap<>();
        for (AxisCount row : rows) {
            // The headcount CASE has no bucket for a company that reports none; a null label is that
            // row, not a band.
            if (row.label() != null) {
                countsByAxis.computeIfAbsent(row.axis(), axis -> new LinkedHashMap<>())
                        .put(row.label(), row.count());
            }
        }
        return new FacetCountsResponse(
                countsOf(countsByAxis, FacetAxis.INDUSTRY),
                countsOf(countsByAxis, FacetAxis.EMPLOYEE_SIZE),
                countsOf(countsByAxis, FacetAxis.REVENUE),
                countsOf(countsByAxis, FacetAxis.MARKET_SEGMENT));
    }

    /**
     * The Company Keywords box. Ranked like the company picker: a prefix match beats one buried
     * mid-word, then the biggest slice of the market first.
     *
     * <p>Reads {@code app_lm_apollo_keywords}, which V33 materialises because the same question asked
     * of the universe directly cannot be made cheap by any parameter the caller sends. That is also
     * why these counts stay over the whole universe while every accordion's follow the selection.
     *
     * <p>{@code LIKE} rather than {@code ILIKE} for the reason {@code arrayLiteral} gives: every
     * keyword in the table is already lower-case.
     */
    public List<FacetCount> keywordSuggestions(String query, int limit, int minCompanies) {
        String pattern = escapeLikePattern(query.toLowerCase(Locale.ROOT));
        return jdbc.sql("""
                        SELECT keyword AS label, company_count AS count
                        FROM app_lm_apollo_keywords
                        WHERE keyword LIKE :contains ESCAPE '\\'
                          AND company_count >= :minCompanies
                        ORDER BY (keyword LIKE :prefix ESCAPE '\\') DESC, company_count DESC, 1
                        LIMIT :limit
                        """)
                .param("contains", "%" + pattern + "%")
                .param("prefix", pattern + "%")
                .param("minCompanies", minCompanies)
                .param("limit", limit)
                .query(ScopeBreakdown.class)
                .list()
                .stream()
                .map(row -> new FacetCount(row.label(), row.label(), row.count()))
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
    private List<SectorGroup> sectorGroups() {
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
            groups.add(new SectorGroup(groupName, counted));
        });
        return groups;
    }

    /**
     * The Market Segments accordion: how many companies each segment's keywords reach.
     *
     * <p>Segments <b>overlap</b> — a company can be B2B and SaaS and Fintech at once — so this counts
     * per segment rather than grouping the table by one of them, and the counts add up to more than
     * the universe. That is the honest answer for an axis where a company holds several positions.
     *
     * <p>Segments keep the file's order, not size order: this is a short fixed list the eye learns,
     * and re-ranking it on every pipeline load would move the chip out from under the hand.
     */
    private List<FacetCount> marketSegmentFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        String sql = """
                SELECT segment.segment_name AS label, segment.segment_count AS count
                FROM %s
                """.formatted(segmentTally("TRUE", params));
        Map<String, Long> countBySegment = new LinkedHashMap<>();
        bind(jdbc.sql(sql), params).query(ScopeBreakdown.class).list()
                .forEach(row -> countBySegment.put(row.label(), row.count()));
        return marketSegments.segments().keySet().stream()
                .map(segment -> new FacetCount(segment, segment, countBySegment.getOrDefault(segment, 0L)))
                .toList();
    }

    /**
     * The Location accordion — the only axis that offers its values without counting them.
     *
     * <p>The live vocabulary is the six GCC countries, which is why the mockup's six fixed chips
     * turned out to be the whole list rather than a sample. Six pills are a set whose shape is the
     * information, and a number on each was noise on the one accordion that reads as chips.
     *
     * <p><b>The count still decides the order and never leaves the database.</b> Largest market first
     * keeps the rail scannable, and unlike the counts themselves that ordering is stable — it does
     * not move when the consultant selects something.
     */
    private List<FacetValue> countryFacets() {
        return jdbc.sql("""
                        SELECT company_country
                        FROM app_lm_apollo_companies
                        WHERE company_country IS NOT NULL AND company_country <> ''
                        GROUP BY 1
                        ORDER BY count(*) DESC, 1
                        """)
                .query(String.class)
                .list()
                .stream()
                .map(country -> new FacetValue(country, country))
                .toList();
    }

    /**
     * The two size accordions, in enum order and including any band that counts zero: a band silently
     * missing from the sidebar reads as "no such size", where a zero reads as "none in this market".
     */
    private List<FacetCount> employeeBandFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = universeBandCounts(employeeBandCase(params), params);
        return Arrays.stream(EmployeeBand.values())
                .map(band -> new FacetCount(band.value(), band.label(),
                        counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    /** The Revenue accordion, Unknown included — see {@link RevenueBand#R_UNKNOWN}. */
    private List<FacetCount> revenueBandFacets() {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Long> counts = universeBandCounts(revenueBandCase(params), params);
        return Arrays.stream(RevenueBand.values())
                .map(band -> new FacetCount(band.value(), band.label(),
                        counts.getOrDefault(band.value(), 0L)))
                .toList();
    }

    private Map<String, Long> universeBandCounts(String bandExpression, Map<String, Object> params) {
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
     * Every segment counted in <b>one</b> pass, then unpivoted back into a row per segment.
     *
     * <p>Segments <b>overlap</b> — a company can be B2B and SaaS and Fintech at once — so each needs
     * its own count and no single {@code GROUP BY} can produce them. The obvious shapes both cost
     * more than this one: eleven separate statements, or one statement joining the table to a
     * vocabulary relation, each re-read the same rows eleven times (measured at 134 ms over the live
     * 71,822 against 71 ms here, even with {@code idx_lm_apollo_kw} doing the probing). A conditional
     * aggregate answers all eleven while the row is already in hand.
     *
     * <p>The {@code LATERAL (VALUES …)} turns that one wide row back into the {@code (name, count)}
     * pairs every other axis produces, so the caller has one shape to map rather than two. A segment
     * no company carries comes back as a plain zero.
     */
    private String segmentTally(String whereClause, Map<String, Object> params) {
        List<String> tallies = new ArrayList<>();
        List<String> unpivoted = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<String>> segment : marketSegments.segments().entrySet()) {
            String column = "segment" + index;
            tallies.add("count(*) FILTER (WHERE keywords && %s) AS %s".formatted(
                    arrayLiteral(segment.getValue(), "segmentVocab" + index, params), column));
            String nameParam = "segmentName" + index;
            params.put(nameParam, segment.getKey());
            unpivoted.add("(:%s, tally.%s)".formatted(nameParam, column));
            index++;
        }
        return """
                (SELECT %s FROM app_lm_apollo_companies WHERE %s) AS tally,
                LATERAL (VALUES %s) AS segment(segment_name, segment_count)
                """.formatted(String.join(", ", tallies), whereClause, String.join(", ", unpivoted));
    }

    private static Map<String, Long> countsOf(Map<String, Map<String, Long>> countsByAxis,
                                              FacetAxis axis) {
        return countsByAxis.getOrDefault(axis.wireName(), Map.of());
    }

    /** One row of the counts statement: which accordion, which option, how many. */
    private record AxisCount(String axis, String label, long count) {}
}
