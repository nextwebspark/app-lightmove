package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The one triage write JPA cannot express: an insert that ignores a company the mandate already
 * holds.
 *
 * <p>Read-then-insert is a race against {@code app_lm_project_triage_company_uk}. Two "Add all"
 * clicks, or one racing a single-row add, both pass the check and the second fails the whole batch.
 * V32's header describes this statement; this is it.
 */
@Repository
@RequiredArgsConstructor
public class TriageCompanyWriter {

    private static final String INSERT_HEAD = """
            INSERT INTO app_lm_project_triage_company (
                project_id, apollo_account_id, status, company_name, industry, company_country,
                company_city, num_employees, annual_revenue, website, logo_url, added_by)
            VALUES
            """;

    private static final String IGNORE_HELD = """

            ON CONFLICT (project_id, apollo_account_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Snapshots these companies into the mandate's universe and answers how many were new to it.
     *
     * <p>One multi-row statement rather than a JDBC batch, because the answer has to be exact: a
     * batch reports per-statement counts the driver is free to return as {@code SUCCESS_NO_INFO},
     * and "added" is a number the toast states to the user. Every value is bound — the row template
     * generates placeholder names, it never interpolates a value.
     */
    public int insertIgnoringHeld(UUID projectId, UUID addedBy, List<CompanyRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("addedBy", addedBy);
        params.put("status", TriageCompanyStatus.IN_UNIVERSE.name());

        StringBuilder sql = new StringBuilder(INSERT_HEAD);
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) {
                sql.append(",\n");
            }
            sql.append(rowPlaceholders(index, rows.get(index), params));
        }
        return jdbc.update(sql.append(IGNORE_HELD).toString(), params);
    }

    private static final String ROW_PLACEHOLDERS =
            "(:projectId, :accountId%1$d, :status, :companyName%1$d, :industry%1$d, "
                    + ":companyCountry%1$d, :companyCity%1$d, :numEmployees%1$d, :annualRevenue%1$d, "
                    + ":website%1$d, :logoUrl%1$d, :addedBy)";

    private static String rowPlaceholders(int index, CompanyRow row, Map<String, Object> params) {
        params.put("accountId" + index, row.apolloAccountId());
        params.put("companyName" + index, row.companyName());
        params.put("industry" + index, row.industry());
        params.put("companyCountry" + index, row.companyCountry());
        params.put("companyCity" + index, row.companyCity());
        params.put("numEmployees" + index, row.numEmployees());
        params.put("annualRevenue" + index, row.annualRevenue());
        params.put("website" + index, row.website());
        params.put("logoUrl" + index, row.logoUrl());
        return ROW_PLACEHOLDERS.formatted(index);
    }
}
