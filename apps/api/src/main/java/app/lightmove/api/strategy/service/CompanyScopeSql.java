package app.lightmove.api.strategy.service;

import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.FacetAxis;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.NumericRange;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Every criterion a {@link CompanyScope} states, rendered as SQL over {@code app_lm_apollo_companies}
 * and addressable one axis at a time.
 *
 * <p>The reason this is a type rather than a private method on the query service is
 * {@link #excluding(FacetAxis)}. A facet count is taken with everything applied <b>but the axis being
 * counted</b>, so the same scope has to render five different WHERE clauses that agree with each other
 * in every respect except one. Keeping each criterion under the axis it belongs to makes that one
 * lookup instead of four hand-maintained variants of {@code buildWhere}.
 *
 * <p>Criteria no accordion counts — the countries, the company keywords, the off-limits list, the
 * results table's name filter — are unconditional and survive every exclusion. Off-limits especially:
 * a barred company must not be counted into a chip the consultant is about to click.
 *
 * <p><b>Every rendering shares one parameter map, deliberately.</b> Dropping an axis leaves its bound
 * values behind, and that is harmless: named-parameter binding resolves only the placeholders the
 * parsed statement actually contains, so a value nothing references is never sent.
 */
final class CompanyScopeSql {

    private final Map<FacetAxis, String> clauseByAxis;
    private final List<String> unconditionalClauses;
    private final Map<String, Object> params;

    private CompanyScopeSql(Map<FacetAxis, String> clauseByAxis, List<String> unconditionalClauses,
                            Map<String, Object> params) {
        this.clauseByAxis = clauseByAxis;
        this.unconditionalClauses = unconditionalClauses;
        this.params = params;
    }

    /**
     * Every criterion the sidebar can set, split by the axis that owns it. Each is omitted entirely
     * when it selects nothing, so an untouched filter renders as {@code TRUE} and returns the whole
     * universe — the right opening state for a search screen.
     */
    static CompanyScopeSql of(CompanyScope scope, MarketSegments marketSegments) {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<FacetAxis, String> clauseByAxis = new EnumMap<>(FacetAxis.class);
        List<String> unconditional = new ArrayList<>();

        if (!scope.industries().isEmpty()) {
            // Lower-cased on both sides. Apollo's vocabulary is already lower-case throughout, but a
            // filter saved from a facet response should not depend on that staying true.
            clauseByAxis.put(FacetAxis.INDUSTRY, "lower(industry) IN (:industries)");
            params.put("industries", lowered(scope.industries()));
        }
        List<String> segmentKeywords = marketSegments.keywordsOfAll(scope.marketSegments());
        if (!segmentKeywords.isEmpty()) {
            clauseByAxis.put(FacetAxis.MARKET_SEGMENT,
                    "keywords && " + arrayLiteral(segmentKeywords, "segKw", params));
        }
        // A custom range and the predefined rows are the panel's two modes, so the range wins outright
        // rather than intersecting: a consultant who typed 250-400 means that, not "and also 201-500".
        String employeeClause = scope.employeeRange() != null
                ? rangeClause("num_employees", scope.employeeRange(), "empRange", params)
                : employeeBandClause(scope.employeeBands(), params);
        if (employeeClause != null) {
            clauseByAxis.put(FacetAxis.EMPLOYEE_SIZE, employeeClause);
        }
        String revenueClause = scope.revenueRange() != null
                ? rangeClause("annual_revenue", scope.revenueRange(), "revRange", params)
                : revenueBandClause(scope.revenueBands(), params);
        if (revenueClause != null) {
            clauseByAxis.put(FacetAxis.REVENUE, revenueClause);
        }

        if (!scope.keywords().isEmpty()) {
            unconditional.add("keywords && " + arrayLiteral(lowered(scope.keywords()), "kw", params));
        }
        // Unconditional rather than an axis: no accordion counts Location, so nothing ever excludes
        // the country criterion and it belongs beside the keywords and the off-limits list.
        if (!scope.countries().isEmpty()) {
            unconditional.add("company_country IN (:countries)");
            params.put("countries", scope.countries());
        }
        if (!scope.offLimitsAccountIds().isEmpty()) {
            unconditional.add("apollo_account_id NOT IN (:offLimitsIds)");
            params.put("offLimitsIds", scope.offLimitsAccountIds());
        }
        if (scope.nameQuery() != null) {
            unconditional.add("company_name ILIKE :nameQuery ESCAPE '\\'");
            params.put("nameQuery", "%" + escapeLikePattern(scope.nameQuery()) + "%");
        }
        return new CompanyScopeSql(clauseByAxis, unconditional, params);
    }

    /** Every criterion, ANDed — what the results table, its total and the report aggregates ask. */
    WhereClause whole() {
        return rendered(null);
    }

    /** Every criterion but this axis's — what counting that axis's own rows asks. */
    WhereClause excluding(FacetAxis axis) {
        return rendered(axis);
    }

    /** The bound values behind every rendering, superset included. */
    Map<String, Object> params() {
        return params;
    }

    private WhereClause rendered(FacetAxis excluded) {
        List<String> clauses = new ArrayList<>(unconditionalClauses);
        clauseByAxis.forEach((axis, clause) -> {
            if (axis != excluded) {
                clauses.add(clause);
            }
        });
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
     * with no figure is excluded from every <i>numeric</i> band — it cannot be shown to fall in one —
     * so Unknown is how those 64,690 companies are reached at all.
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

    /**
     * A CASE mapping each headcount to its band's wire token, built from the enum's own bounds — so
     * the chip counts and the filter that runs when the chip is clicked can never disagree. A band
     * counted by one set of numbers and filtered by another is the bug this shape prevents.
     */
    static String employeeBandCase(Map<String, Object> params) {
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
    static String revenueBandCase(Map<String, Object> params) {
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
    static String arrayLiteral(List<String> values, String prefix, Map<String, Object> params) {
        List<String> placeholders = new ArrayList<>(values.size());
        int index = 0;
        for (String value : values) {
            String name = prefix + index++;
            params.put(name, value);
            placeholders.add(":" + name);
        }
        return "ARRAY[" + String.join(", ", placeholders) + "]::text[]";
    }

    static List<String> lowered(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    /** Backslash-escape LIKE's wildcards so the user's text matches literally. */
    static String escapeLikePattern(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Map<String, Object> params) {
        JdbcClient.StatementSpec bound = spec;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            bound = bound.param(entry.getKey(), entry.getValue());
        }
        return bound;
    }
}
