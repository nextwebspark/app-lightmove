package app.lightmove.api.triagecompany.service;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.service.TriagedCompanyLookup;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Excludes a project's triaged companies from {@code strategy}'s search as a correlated
 * {@code NOT EXISTS} (backed by {@code app_lm_project_triage_company_uk}), bound once however many.
 */
@Component
class TriagedCompanyLookupAdapter implements TriagedCompanyLookup {

    @Override
    public CompanyExclusion exclusionFor(UUID projectId) {
        return new CompanyExclusion("""
                NOT EXISTS (
                    SELECT 1 FROM app_lm_project_triage_company t
                    WHERE t.project_id = :triagedProjectId AND t.apollo_account_id = a.apollo_account_id
                )""",
                Map.of("triagedProjectId", projectId));
    }
}
