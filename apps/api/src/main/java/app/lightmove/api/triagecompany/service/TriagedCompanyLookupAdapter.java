package app.lightmove.api.triagecompany.service;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.service.TriagedCompanyLookup;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place {@code triagecompany} reaches back into {@code strategy}'s search: hands back a
 * predicate excluding every company this project has already triaged, so {@code strategy} can apply
 * it without importing anything of ours. {@code strategy} owns {@link TriagedCompanyLookup}; this
 * only implements it.
 *
 * <p>A correlated {@code NOT EXISTS} rather than the id list {@code TriageCompanyRepository} could
 * still hand back: bound once regardless of how much a mandate has triaged, and backed by {@code
 * app_lm_project_triage_company_uk (project_id, apollo_account_id)} on the {@code project_id} side —
 * the same index the id-list form relied on to produce the ids in the first place, just never
 * leaving the database to do it.
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
