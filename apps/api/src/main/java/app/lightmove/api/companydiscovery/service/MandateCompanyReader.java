package app.lightmove.api.companydiscovery.service;

import app.lightmove.api.companydiscovery.model.HeldCompanies;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.core.text.service.WebsiteDomain;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every key a mandate's filed companies can be recognised by, in one query.
 *
 * <p>One read per request rather than one per candidate: twenty-five candidates against a mandate
 * holding four hundred companies is one statement either way, and the per-candidate shape would be
 * twenty-five round trips behind a call that has already cost seconds.
 *
 * <p>A projection over the four identifying columns rather than loading the entities, because
 * nothing here wants a company — only whether it is one we have seen.
 */
@Service
@RequiredArgsConstructor
public class MandateCompanyReader {

    private static final String HELD = """
            SELECT apollo_account_id, company_name, website, company_linkedin_url
            FROM app_lm_project_triage_company
            WHERE project_id = :projectId
            """;

    private final JdbcClient jdbc;

    @Transactional(readOnly = true)
    public HeldCompanies heldBy(UUID projectId) {
        Set<String> apolloIds = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> domains = new HashSet<>();
        Set<String> slugs = new HashSet<>();

        List<Map<String, Object>> rows = jdbc.sql(HELD)
                .param("projectId", projectId)
                .query()
                .listOfRows();
        for (Map<String, Object> row : rows) {
            add(apolloIds, (String) row.get("apollo_account_id"));
            // Lower-cased here and lower-cased on the candidate, so the two sides are keyed the same
            // way rather than nearly the same way.
            add(names, lower((String) row.get("company_name")));
            add(domains, WebsiteDomain.of((String) row.get("website")));
            add(slugs, LinkedInUrls.companySlugOrNull((String) row.get("company_linkedin_url")));
        }
        return new HeldCompanies(apolloIds, names, domains, slugs);
    }

    private static void add(Set<String> into, String value) {
        if (value != null && !value.isBlank()) {
            into.add(value);
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
