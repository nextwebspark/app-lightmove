package app.lightmove.api.company.service;

import app.lightmove.api.company.constant.EmployeeBand;
import app.lightmove.api.company.constant.RevenueBand;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Translates a resolved strategy scope into the Elasticsearch-DSL body CoreSignal's multi-source
 * search accepts (their only search dialect). Pure translation — no HTTP, no state — so the exact
 * JSON is unit-tested without a network.
 *
 * <p>Two rules guard the credit spend, mirroring the local query engine's {@code buildWhere}:
 * the sector/tag anchor is required (callers must not search an anchorless scope at all), and a
 * scope that names no market is pinned to the full GCC set rather than searched worldwide — an
 * unbounded search is a credit bomb, not a bigger result.
 *
 * <p>The numeric band bounds are re-declared here from the band enums' names (the enums themselves
 * carry only the wire range-strings, matched verbatim against warehouse columns). Revenue bounds
 * assume the sort/range field is USD — unverified against live data; see {@link #REVENUE_FIELD}.
 */
@Service
@RequiredArgsConstructor
public class CoreSignalQueryBuilder {

    /**
     * The revenue field used for both range filtering and the revenue-desc sort. Docs-verified as
     * sortable; whether its unit is USD (assumed by the band bounds) and how well it is populated
     * is a runtime-verify item — if live results look wrong, the first thing to try is the plain
     * {@code revenue_annual} field.
     */
    public static final String REVENUE_FIELD = "revenue_annual.source_1_annual_revenue.annual_revenue";

    /** No market selected still means "our markets", never "the world" — searching is metered. */
    public static final List<String> GCC_COUNTRY_CODES = List.of("AE", "SA", "KW", "QA", "BH", "OM");

    private static final long MILLION = 1_000_000L;

    private final ObjectMapper json;

    /** The full es_dsl request body for a criteria, as a JSON string ready to POST. */
    public String searchBody(CoreSignalSearchCriteria criteria) {
        if (!criteria.hasAnchor()) {
            throw new IllegalArgumentException("refusing to build an anchorless CoreSignal search");
        }

        ObjectNode root = json.createObjectNode();
        ArrayNode must = root.putObject("query").putObject("bool").putArray("must");

        must.add(anchorClause(criteria));
        must.add(countryClause(criteria));
        if (!criteria.employeeBands().isEmpty()) {
            must.add(rangeShouldClause(criteria.employeeBands().stream()
                    .map(this::employeeRange).toList(), "employees_count"));
        }
        if (!criteria.revenueBands().isEmpty()) {
            must.add(rangeShouldClause(criteria.revenueBands().stream()
                    .map(this::revenueRange).toList(), REVENUE_FIELD));
        }

        ArrayNode sort = root.putArray("sort");
        ObjectNode revenueSort = sort.addObject().putObject(REVENUE_FIELD);
        revenueSort.put("order", "desc");
        revenueSort.put("missing", "_last");
        sort.addObject().put("id", "asc");

        return json.writeValueAsString(root);
    }

    /** Match through an industry label OR a tag — one of them must hold, same as the local engine. */
    private ObjectNode anchorClause(CoreSignalSearchCriteria criteria) {
        ObjectNode clause = json.createObjectNode();
        ObjectNode bool = clause.putObject("bool");
        bool.put("minimum_should_match", 1);
        ArrayNode should = bool.putArray("should");
        if (!criteria.industries().isEmpty()) {
            should.addObject().putObject("terms").putArray("industry")
                    .addAll(textNodes(criteria.industries()));
        }
        if (!criteria.tags().isEmpty()) {
            should.addObject().putObject("terms").putArray("categories_and_keywords")
                    .addAll(textNodes(criteria.tags()));
        }
        return clause;
    }

    private ObjectNode countryClause(CoreSignalSearchCriteria criteria) {
        List<String> countries = criteria.countryIso2Codes().isEmpty()
                ? GCC_COUNTRY_CODES
                : criteria.countryIso2Codes();
        ObjectNode clause = json.createObjectNode();
        clause.putObject("terms").putArray("hq_country_iso2").addAll(textNodes(countries));
        return clause;
    }

    /** OR across the selected bands of one axis — a company matches any one of its ranges. */
    private ObjectNode rangeShouldClause(List<ObjectNode> ranges, String field) {
        ObjectNode clause = json.createObjectNode();
        ObjectNode bool = clause.putObject("bool");
        bool.put("minimum_should_match", 1);
        ArrayNode should = bool.putArray("should");
        for (ObjectNode range : ranges) {
            should.addObject().putObject("range").set(field, range);
        }
        return clause;
    }

    /** Inclusive bounds, matching the band labels literally ("51-200" means 51 through 200). */
    private ObjectNode employeeRange(EmployeeBand band) {
        return switch (band) {
            case B_1_10 -> bounds(1L, 10L, true);
            case B_11_50 -> bounds(11L, 50L, true);
            case B_51_200 -> bounds(51L, 200L, true);
            case B_201_500 -> bounds(201L, 500L, true);
            case B_501_1000 -> bounds(501L, 1000L, true);
            case B_1001_5000 -> bounds(1001L, 5000L, true);
            case B_5001_10000 -> bounds(5001L, 10000L, true);
            case B_10000_PLUS -> bounds(10001L, null, true);
        };
    }

    /** Half-open [min, max) in USD, so adjacent bands tile without gap or overlap. */
    private ObjectNode revenueRange(RevenueBand band) {
        return switch (band) {
            case R_UNDER_5M -> bounds(null, 5 * MILLION, false);
            case R_5M_25M -> bounds(5 * MILLION, 25 * MILLION, false);
            case R_25M_100M -> bounds(25 * MILLION, 100 * MILLION, false);
            case R_100M_500M -> bounds(100 * MILLION, 500 * MILLION, false);
            case R_500M_1B -> bounds(500 * MILLION, 1000 * MILLION, false);
            case R_1B_5B -> bounds(1000 * MILLION, 5000 * MILLION, false);
            case R_5B_PLUS -> bounds(5000 * MILLION, null, false);
        };
    }

    private ObjectNode bounds(Long min, Long max, boolean maxInclusive) {
        ObjectNode range = json.createObjectNode();
        if (min != null) {
            range.put("gte", min);
        }
        if (max != null) {
            range.put(maxInclusive ? "lte" : "lt", max);
        }
        return range;
    }

    private ArrayNode textNodes(List<String> values) {
        ArrayNode array = json.createArrayNode();
        values.forEach(array::add);
        return array;
    }
}
