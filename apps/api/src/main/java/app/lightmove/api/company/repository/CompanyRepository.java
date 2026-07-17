package app.lightmove.api.company.repository;

import app.lightmove.api.company.constant.TaxonomyType;
import app.lightmove.api.company.dto.CompanyDtos.Company;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchFilter;
import app.lightmove.api.company.model.CompanyTaxonomyRow;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hand-written JDBC over {@code app_lm_companies} — the read-only, shared company universe.
 *
 * <p>Not a Spring Data interface: the tag filter needs Postgres's array-overlap operator ({@code &&}),
 * which JPQL cannot express, and this table has no domain behaviour to protect behind a JPA entity —
 * {@link #search} projects straight into {@link Company}.
 *
 * <p>The array-overlap filter binds via {@code string_to_array(:tags, chr(31))} rather than a
 * {@code java.sql.Array} parameter: Spring's core JDBC parameter binding does not auto-convert a Java
 * array for {@code Types.ARRAY} (verified against {@code StatementCreatorUtils} — there is no
 * {@code Connection.createArrayOf} call in it), so binding one correctly means reaching for a raw
 * {@code Connection} from inside the repository. Joining on a control character no taxonomy value will
 * ever contain and letting Postgres split it back into an array server-side avoids that entirely.
 */
@Repository
@RequiredArgsConstructor
public class CompanyRepository {

    /**
     * Unit separator (code point 31) — joins the tag list for {@code string_to_array(:tags, chr(31))}
     * below. Built from the numeric code point rather than a literal/escaped character in source, so
     * this constant doesn't depend on a control character round-tripping correctly through file I/O.
     */
    private static final String TAG_DELIMITER = String.valueOf((char) 31);

    private final NamedParameterJdbcTemplate jdbc;

    /** Distinct {@code primary_industry} values with how many rows currently carry each. */
    public List<CompanyTaxonomyRow> distinctPrimaryIndustries() {
        String sql = """
                SELECT primary_industry AS name, count(*) AS company_count
                FROM app_lm_companies
                WHERE primary_industry IS NOT NULL
                GROUP BY primary_industry
                """;
        return jdbc.getJdbcOperations().query(sql, (rs, rowNum) -> new CompanyTaxonomyRow(
                TaxonomyType.PRIMARY_TYPE, rs.getString("name"), rs.getInt("company_count")));
    }

    /** Distinct {@code industry_tags} elements with how many rows carry each. */
    public List<CompanyTaxonomyRow> distinctIndustryTags() {
        String sql = """
                SELECT tag AS name, count(*) AS company_count
                FROM app_lm_companies, unnest(industry_tags) AS tag
                WHERE industry_tags IS NOT NULL
                GROUP BY tag
                """;
        return jdbc.getJdbcOperations().query(sql, (rs, rowNum) -> new CompanyTaxonomyRow(
                TaxonomyType.TAG, rs.getString("name"), rs.getInt("company_count")));
    }

    /**
     * The parameterized company search. Each filter is applied only when present on {@code filter} —
     * callers are expected to have already short-circuited the case where every taxonomy filter is
     * empty (see {@code CompanySearchService}); an all-empty filter here matches every row, same as a
     * plain {@code WHERE true}.
     */
    public Page<Company> search(CompanySearchFilter filter, int page, int size) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filter.primaryTypes() != null && !filter.primaryTypes().isEmpty()) {
            where.append(" AND primary_industry IN (:primaryTypes)");
            params.addValue("primaryTypes", filter.primaryTypes());
        }
        if (filter.tags() != null && !filter.tags().isEmpty()) {
            where.append(" AND industry_tags && string_to_array(:tags, chr(31))");
            params.addValue("tags", String.join(TAG_DELIMITER, filter.tags()));
        }
        if (filter.country() != null) {
            where.append(" AND hq_country = :country");
            params.addValue("country", filter.country());
        }
        if (filter.employeeCountMin() != null) {
            where.append(" AND employee_count >= :employeeCountMin");
            params.addValue("employeeCountMin", filter.employeeCountMin());
        }
        if (filter.employeeCountMax() != null) {
            where.append(" AND employee_count <= :employeeCountMax");
            params.addValue("employeeCountMax", filter.employeeCountMax());
        }
        if (filter.revenueMinUsd() != null) {
            where.append(" AND revenue_usd >= :revenueMinUsd");
            params.addValue("revenueMinUsd", filter.revenueMinUsd());
        }
        if (filter.revenueMaxUsd() != null) {
            where.append(" AND revenue_usd <= :revenueMaxUsd");
            params.addValue("revenueMaxUsd", filter.revenueMaxUsd());
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM app_lm_companies " + where, params, Long.class);

        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);
        String sql = """
                SELECT id, name, website, domain, primary_industry, industry_tags,
                       hq_country, employee_count, revenue_usd
                FROM app_lm_companies
                %s
                ORDER BY employee_count DESC NULLS LAST, id
                LIMIT :limit OFFSET :offset
                """.formatted(where);

        List<Company> companies = jdbc.query(sql, params, CompanyRepository::mapRow);
        return new PageImpl<>(companies, PageRequest.of(page, size), total == null ? 0 : total);
    }

    private static Company mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Company(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("website"),
                rs.getString("domain"),
                rs.getString("primary_industry"),
                toStringList(rs.getArray("industry_tags")),
                rs.getString("hq_country"),
                (Integer) rs.getObject("employee_count"),
                (Long) rs.getObject("revenue_usd"));
    }

    private static List<String> toStringList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object[] elements = (Object[]) sqlArray.getArray();
        List<String> values = new ArrayList<>(elements.length);
        for (Object element : elements) {
            values.add((String) element);
        }
        return values;
    }
}
