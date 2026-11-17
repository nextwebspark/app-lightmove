package app.lightmove.api.dataimport.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentTarget;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.ArrayList;
import java.util.List;

/** What one row filed, and the LinkedIn links its own cells named — the file's links, never a stored one. */
record FiledRow(TriageCompanyResponse company, String companySlug, CandidateResponse person, String profileSlug) {

    /** A market company is never researched: its snapshot is the market's, as the Source badge promises. */
    List<ImportEnrichmentTarget> enrichmentTargets() {
        List<ImportEnrichmentTarget> targets = new ArrayList<>(2);
        if (person != null && profileSlug != null) {
            targets.add(new ImportEnrichmentTarget(ImportEnrichmentKind.PERSON, person.id(), profileSlug));
        }
        if (company != null && companySlug != null && company.apolloAccountId() == null) {
            targets.add(new ImportEnrichmentTarget(ImportEnrichmentKind.COMPANY, company.id(), companySlug));
        }
        return targets;
    }
}
