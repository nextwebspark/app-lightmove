package app.lightmove.api.triagecompany.repository;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
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
 * An insert that ignores companies the mandate already holds. Read-then-insert races
 * {@code app_lm_project_triage_company_uk}: two "Add all" clicks would fail the second whole batch.
 */
@Repository
@RequiredArgsConstructor
public class TriageCompanyWriter {

    private static final String INSERT_HEAD = """
            INSERT INTO app_lm_project_triage_company (
                project_id, apollo_account_id, source, status, note, company_name, industry,
                industry_v2_code, industry_v2_label, sector_group,
                company_country, company_city, num_employees, annual_revenue, website,
                company_linkedin_url, founded_year, short_description, logo_url, source_url, added_by)
            VALUES
            """;

    private static final String IGNORE_HELD = """

            ON CONFLICT (project_id, apollo_account_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Answers how many rows were new. One multi-row statement, not a JDBC batch, whose counts may come
     * back {@code SUCCESS_NO_INFO}. Every value is bound; the template generates placeholder names only.
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
                    + ":industryV2Code%1$d, :industryV2Label%1$d, :sectorGroup%1$d, "
                    + ":companyCountry%1$d, :companyCity%1$d, :numEmployees%1$d, :annualRevenue%1$d, "
                    + ":website%1$d, :companyLinkedinUrl%1$d, :foundedYear%1$d, "
                    + ":shortDescription%1$d, :logoUrl%1$d, :sourceUrl, :addedBy)";

    private static String rowPlaceholders(int index, CompanyRow row, Map<String, Object> params) {
        params.put("accountId" + index, row.apolloAccountId());
        params.put("companyName" + index, row.companyName());
        // The same call TriageCompany.fileUnder uses, or a bulk add and a capture could disagree on sector.
        ResolvedIndustry industry = Industries.resolve(row.industry());
        params.put("industry" + index, industry == null ? null : industry.label());
        params.put("industryV2Code" + index, industry == null ? null : industry.linkedInCode());
        params.put("industryV2Label" + index, industry == null ? null : industry.v2Label());
        params.put("sectorGroup" + index, industry == null ? null : industry.sectorGroup());
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
