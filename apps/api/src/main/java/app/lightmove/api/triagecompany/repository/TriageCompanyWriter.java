package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
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
                project_id, apollo_account_id, source, status, note, company_name, industry,
                company_country, company_city, num_employees, annual_revenue, website,
                company_linkedin_url, founded_year, short_description, logo_url, source_url, added_by)
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
     *
     * <p>{@code status}, {@code note} and {@code sourceUrl} are the caller's, not the row's: they say
     * where this act of adding lands, what the consultant remarked while doing it, and which page it
     * was made from, so they are the same for every company in one statement. A bulk add carries none
     * of them beyond the default stage. {@code source} is the door: STRATEGY from the market screens,
     * EXTENSION when a plugin capture resolved against the universe — either way the row carries the
     * full market snapshot and its apollo id.
     */
    public int insertIgnoringHeld(UUID projectId, UUID addedBy, List<CompanyRow> rows,
                                  TriageCompanySource source, TriageCompanyStatus status, String note,
                                  String sourceUrl) {
        if (rows.isEmpty()) {
            return 0;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", projectId);
        params.put("addedBy", addedBy);
        params.put("status", status.name());
        params.put("note", note);
        params.put("sourceUrl", sourceUrl);
        params.put("source", source.name());

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
            "(:projectId, :accountId%1$d, :source, :status, :note, :companyName%1$d, :industry%1$d, "
                    + ":companyCountry%1$d, :companyCity%1$d, :numEmployees%1$d, :annualRevenue%1$d, "
                    + ":website%1$d, :companyLinkedinUrl%1$d, :foundedYear%1$d, "
                    + ":shortDescription%1$d, :logoUrl%1$d, :sourceUrl, :addedBy)";

    private static String rowPlaceholders(int index, CompanyRow row, Map<String, Object> params) {
        params.put("accountId" + index, row.apolloAccountId());
        params.put("companyName" + index, row.companyName());
        params.put("industry" + index, row.industry());
        params.put("companyCountry" + index, row.companyCountry());
        params.put("companyCity" + index, row.companyCity());
        params.put("numEmployees" + index, row.numEmployees());
        params.put("annualRevenue" + index, row.annualRevenue());
        params.put("website" + index, row.website());
        params.put("companyLinkedinUrl" + index, row.companyLinkedinUrl());
        params.put("foundedYear" + index, row.foundedYear());
        params.put("shortDescription" + index, row.shortDescription());
        params.put("logoUrl" + index, row.logoUrl());
        return ROW_PLACEHOLDERS.formatted(index);
    }
}
