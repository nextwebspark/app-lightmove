package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.enrichment.company.model.CachedCompany;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cache's two transactions, on a bean of their own.
 *
 * <p>Its own bean for {@code GeocodedPlaceStore}'s reason: {@code CompanyResearch} calls the vendor
 * between the read and the write, and a vendor call must not sit inside a transaction — a permit wait
 * plus retry backoff would hold a database connection for seconds. {@code REQUIRES_NEW} for
 * {@code TriageCompanyService.applyEnrichment}'s reason: the only caller runs in an
 * {@code AFTER_COMMIT} callback, where the completed transaction's resources are still bound to the
 * thread and joining them writes nothing.
 *
 * <p>The write is an upsert rather than an insert: two mandates capturing the same company at once
 * both research it, and the second must land on the row the first made rather than on the primary
 * key.
 */
@Component
@RequiredArgsConstructor
public class CachedCompanyStore {

    private static final String SELECT = """
            SELECT provider, fetched_at, found, company_name, industry_v2_label, company_country,
                   company_city, employees_linkedin, website, linkedin_url, founded_year, about,
                   logo_url, keywords, raw
            FROM app_lm_company
            WHERE linkedin_slug = ?
            """;

    /**
     * The V2 code is looked up rather than passed in: {@code Industries} is static and reads a file
     * holding one V2 name per universe label, so the code for the vendor's own leaf — which may be
     * any of the 434 — can only come from {@code app_lm_industry_v2}.
     */
    private static final String UPSERT = """
            INSERT INTO app_lm_company (
                linkedin_slug, provider, fetched_at, found, company_name,
                industry_v2_code,
                industry_v2_label, industry_v1, sector_group, company_country, company_city,
                employees_linkedin, website, linkedin_url, founded_year, about, logo_url, keywords,
                raw)
            VALUES (?, ?, now(), ?, ?,
                (SELECT v2_code FROM app_lm_industry_v2 WHERE lower(v2_label) = lower(?)),
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (linkedin_slug) DO UPDATE SET
                provider          = EXCLUDED.provider,
                fetched_at        = EXCLUDED.fetched_at,
                found             = EXCLUDED.found,
                company_name      = EXCLUDED.company_name,
                industry_v2_code  = EXCLUDED.industry_v2_code,
                industry_v2_label = EXCLUDED.industry_v2_label,
                industry_v1       = EXCLUDED.industry_v1,
                sector_group      = EXCLUDED.sector_group,
                company_country   = EXCLUDED.company_country,
                company_city      = EXCLUDED.company_city,
                employees_linkedin = EXCLUDED.employees_linkedin,
                website           = EXCLUDED.website,
                linkedin_url      = EXCLUDED.linkedin_url,
                founded_year      = EXCLUDED.founded_year,
                about             = EXCLUDED.about,
                logo_url          = EXCLUDED.logo_url,
                keywords          = EXCLUDED.keywords,
                raw               = EXCLUDED.raw
            """;

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<CachedCompany> find(String linkedinSlug) {
        return jdbc.query(SELECT, rs -> rs.next() ? Optional.of(read(linkedinSlug, rs)) : Optional.empty(),
                linkedinSlug);
    }

    /** Remembers an answer — including no answer, so a slug the provider does not carry is not re-bought. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remember(String linkedinSlug, String provider, Optional<VendorCompanyRecord> answer) {
        VendorCompanyRecord record = answer.orElse(null);
        ResolvedIndustry resolved =
                record == null ? null : Industries.resolve(record.industry());
        // A label nobody could resolve stays in industry_v2_label and leaves industry_v1 null: the
        // column is a foreign key into app_lm_industry, so the vendor's own spelling cannot go there.
        boolean known = resolved != null && resolved.sectorGroup() != null;

        jdbc.update(UPSERT, ps -> {
            int field = 1;
            ps.setString(field++, linkedinSlug);
            ps.setString(field++, provider);
            ps.setBoolean(field++, record != null);
            ps.setString(field++, record == null ? null : record.companyName());
            ps.setString(field++, record == null ? null : record.industry());
            ps.setString(field++, record == null ? null : record.industry());
            ps.setString(field++, known ? resolved.label() : null);
            ps.setString(field++, known ? resolved.sectorGroup() : null);
            ps.setString(field++, record == null ? null : record.companyCountry());
            ps.setString(field++, record == null ? null : record.companyCity());
            ps.setObject(field++, record == null ? null : record.employeesInLinkedin(), Types.INTEGER);
            ps.setString(field++, record == null ? null : record.website());
            ps.setString(field++, record == null ? null : record.linkedinUrl());
            ps.setObject(field++, record == null ? null : record.foundedYear(), Types.INTEGER);
            ps.setString(field++, record == null ? null : record.about());
            ps.setString(field++, record == null ? null : record.logoUrl());
            List<String> keywords = record == null ? List.of() : record.keywords();
            if (keywords == null || keywords.isEmpty()) {
                ps.setNull(field++, Types.ARRAY);
            } else {
                ps.setArray(field++, ps.getConnection()
                        .createArrayOf("text", keywords.toArray(String[]::new)));
            }
            ps.setString(field, record == null ? null : record.raw());
        });
    }

    private static CachedCompany read(String linkedinSlug, ResultSet rs) throws SQLException {
        String provider = rs.getString("provider");
        Instant fetchedAt = rs.getTimestamp("fetched_at").toInstant();
        if (!rs.getBoolean("found")) {
            return new CachedCompany(provider, fetchedAt, null);
        }
        return new CachedCompany(provider, fetchedAt, new VendorCompanyRecord(
                linkedinSlug,
                rs.getString("company_name"),
                rs.getString("industry_v2_label"),
                rs.getString("company_country"),
                rs.getString("company_city"),
                (Integer) rs.getObject("employees_linkedin"),
                rs.getString("website"),
                rs.getString("linkedin_url"),
                (Integer) rs.getObject("founded_year"),
                rs.getString("about"),
                rs.getString("logo_url"),
                keywordsOf(rs.getArray("keywords")),
                rs.getString("raw")));
    }

    private static List<String> keywordsOf(Array stored) throws SQLException {
        return stored == null ? List.of() : List.of((String[]) stored.getArray());
    }
}
