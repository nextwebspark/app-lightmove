package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.candidate.model.ProfileResearchOutcome;
import app.lightmove.api.enrichment.candidate.service.ProfileResearch;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentOutcome;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentTarget;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import org.springframework.stereotype.Service;

/**
 * One imported person's research, filed by {@code CandidateService.applyResearch}, which fills only what nobody
 * recorded. No AI background run follows: an import's box consents to LinkedIn research, not to inference.
 */
@Service
class ImportPersonResearch {

    private final ProfileResearch profiles;
    private final CandidateService candidates;
    private final TriageCompanyReadService companyReads;
    private final CompanyResearch companies;
    private final ImportEnrichmentItemStore store;
    private final ImportEnrichmentAllowance allowance;
    private final int runCeiling;

    ImportPersonResearch(ProfileResearch profiles, CandidateService candidates, TriageCompanyReadService companyReads,
                         CompanyResearch companies, ImportEnrichmentItemStore store,
                         ImportEnrichmentAllowance allowance, LightMoveProperties properties) {
        this.profiles = profiles;
        this.candidates = candidates;
        this.companyReads = companyReads;
        this.companies = companies;
        this.store = store;
        this.allowance = allowance;
        this.runCeiling = properties.spreadsheetImport().maxAutoEnrich();
    }

    ImportEnrichmentOutcome research(ClaimedEnrichmentItem item) {
        try {
            candidates.requireCandidate(item.workspaceId(), item.projectId(), item.targetId());
        } catch (ApiException removed) {
            return ImportEnrichmentOutcome.UNAVAILABLE;
        }
        ProfileResearchOutcome outcome = profiles.researchOrFetch(item.workspaceId(),
                LinkedInUrls.profileUrlOf(item.linkedinSlug()), () -> allowance.requirePaidRead(item));
        if (outcome instanceof ProfileResearchOutcome.Fetched || outcome instanceof ProfileResearchOutcome.NotFound) {
            allowance.recordPaidRead(item);
        }
        return switch (outcome) {
            case ProfileResearchOutcome.Refused refused -> ImportEnrichmentOutcome.REFUSED;
            case ProfileResearchOutcome.Unavailable unavailable -> ImportEnrichmentOutcome.UNAVAILABLE;
            case ProfileResearchOutcome.NotFound notFound -> ImportEnrichmentOutcome.NOT_FOUND;
            case ProfileResearchOutcome.OnFile onFile -> {
                candidates.applyResearchWithoutAi(item.projectId(), item.targetId(), onFile.research());
                yield ImportEnrichmentOutcome.ALREADY_RESEARCHED;
            }
            case ProfileResearchOutcome.Fetched fetched -> {
                candidates.applyResearchWithoutAi(item.projectId(), item.targetId(), fetched.research());
                queueEmployerPage(item, fetched.research());
                yield ImportEnrichmentOutcome.RESEARCHED;
            }
        };
    }

    /** A pageless company the person's research names, by the same name, is researched from that page too. */
    private void queueEmployerPage(ClaimedEnrichmentItem item, EnrichedProfile research) {
        String employerSlug = LinkedInUrls.companySlugOrNull(research.employerLinkedinUrl());
        if (employerSlug == null || !companies.isEnabled()) {
            return;
        }
        CandidateResponse person = candidates.get(item.workspaceId(), item.projectId(), item.targetId(),
                ProjectAudience.STAFF);
        if (person.triageCompanyId() == null) {
            return;
        }
        TriageCompanyResponse company = companyReads.get(item.workspaceId(), item.projectId(),
                person.triageCompanyId());
        boolean pageless = company.apolloAccountId() == null
                && LinkedInUrls.companySlugOrNull(company.companyLinkedinUrl()) == null;
        if (pageless && CompanyResearch.namesTheSameCompany(company.companyName(), research.employerName())) {
            store.queueOne(item, new ImportEnrichmentTarget(ImportEnrichmentKind.COMPANY, company.id(),
                    employerSlug), runCeiling);
        }
    }
}
