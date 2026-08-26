package app.lightmove.api.strategy.service;

import static app.lightmove.api.strategy.service.CompanyScopeSql.bind;
import static app.lightmove.api.strategy.service.CompanyScopeSql.escapeLikePattern;

import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.EmployeeBand;
import app.lightmove.api.strategy.constant.RevenueBand;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.CompanyScope;
import app.lightmove.api.strategy.model.ScopeBreakdown;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The company universe, read as rows and as report aggregates. The universe is
 * {@code app_lm_apollo_companies} — 71,822 GCC companies loaded by the pipeline, read-only to this
 * application — and it is the only one; the brightdata warehouse copy this service used to sit beside
 * is gone.
 *
 * <p>{@code JdbcClient} rather than JPA, for the reason the deleted sibling gave and this one
 * inherits: every useful read here is an aggregate or a filtered projection over ETL-owned reference
 * data, and an entity would buy identity, dirty checking and a lifecycle for rows the application
 * must never write.
 *
 * <p>Two things about Apollo shape what a scope can ask:
 *
 * <ul>
 *   <li><b>Size arrives raw.</b> {@code num_employees} and {@code annual_revenue} are figures, not
 *       pre-bucketed range strings, so a band selection becomes an OR of numeric ranges built from
 *       {@link EmployeeBand} / {@link RevenueBand}. Those enums own the bounds, {@link CompanyScopeSql}
 *       owns the SQL they turn into, and this service owns neither.
 *   <li><b>Revenue is sparse.</b> 7,132 rows in 71,822 carry a figure. {@link RevenueBand#R_UNKNOWN}
 *       is therefore a selectable band rendering as {@code annual_revenue IS NULL}, so the missing
 *       nine-tenths are something a consultant can count and choose, rather than a silent exclusion.
 * </ul>
 *
 * <p>What the sidebar renders is not here: {@link CompanyFacetService} owns the market's shape and
 * the counts one selection cuts from it.
 */
@Service
@RequiredArgsConstructor
public class ApolloCompanyQueryService {

    /**
     * Every column the list and the write-path snapshots need, in one place so they cannot drift.
     *
     * <p>What the universe's other columns hold is not a fact about the company — Apollo's own CRM
     * state, its AI-workflow scratch, the loader's bookkeeping and the ids of other systems.
     */
    private static final String ROW_COLUMNS = """
            apollo_account_id, company_name, industry, company_country, company_city,
            num_employees, annual_revenue, website, logo_url,
            short_description, founded_year,
            company_linkedin_url, facebook_url, twitter_url,
            company_phone, company_state, company_address, parent_company,
            total_funding, latest_funding, latest_funding_amount, last_raised_at,
            number_of_retail_locations, keywords, technologies, sic_codes, naics_codes""";

    private final JdbcClient jdbc;
    private final MarketSegments marketSegments;

    /** How many companies the scope matches. An empty scope is the whole universe, not nothing. */
    public long count(CompanyScope scope) {
        WhereClause where = scopeSql(scope).whole();
        return bind(jdbc.sql("SELECT count(*) FROM app_lm_apollo_companies WHERE " + where.sql()),
                where.params()).query(Long.class).single();
    }

    /**
     * One page of the scope, sorted by a column from {@link CompanySortField}'s allowlist. The caller
     * supplies the page, the size and the sort; the scope itself is resolved server-side from the
     * mandate's saved filter and never from a request parameter.
     */
    public List<CompanyRow> search(CompanyScope scope, CompanySortField sort, SortDirection direction,
                                   int page, int size) {
        WhereClause where = scopeSql(scope).whole();
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
        return bind(jdbc.sql(sql), params).query(COMPANY_ROW_MAPPER).list();
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
     */
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
                .list();
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
        WhereClause where = scopeSql(scope).whole();
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

    private CompanyScopeSql scopeSql(CompanyScope scope) {
        return CompanyScopeSql.of(scope, marketSegments);
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
                intOrNull((Number) rs.getObject("founded_year")),
                rs.getString("company_linkedin_url"),
                rs.getString("facebook_url"),
                rs.getString("twitter_url"),
                rs.getString("company_phone"),
                rs.getString("company_state"),
                rs.getString("company_address"),
                rs.getString("parent_company"),
                (Long) rs.getObject("total_funding"),
                rs.getString("latest_funding"),
                (Long) rs.getObject("latest_funding_amount"),
                rs.getObject("last_raised_at", LocalDate.class),
                (Integer) rs.getObject("number_of_retail_locations"),
                stringList(rs, "keywords"),
                stringList(rs, "technologies"),
                stringList(rs, "sic_codes"),
                stringList(rs, "naics_codes"));
    }

    /** An absent {@code text[]} arrives as a null {@link Array}, not as an empty one. */
    private static List<String> stringList(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        String[] values = (String[]) array.getArray();
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull).toList();
    }

    /** Widen whatever numeric type the driver chose, or keep the absence. */
    private static Integer intOrNull(Number value) {
        return value == null ? null : value.intValue();
    }
}
