package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.billing.usage.service.UsageRecorder;
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
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Researches one imported person or company and files it through the owning service, which fills only what nobody
 * recorded. Every paid read rechecks fair use: the import's pre-check cannot see a follow-on read or a second import.
 */
@Service
class ImportEnrichmentResearch {

    private final ProfileResearch profiles;
    private final CandidateService candidates;
    private final CompanyResearch companies;
    private final TriageCompanyReadService companyReads;
    private final TriageCompanyService triage;
    private final ImportEnrichmentItemStore store;
    private final FairUseGuard fairUse;
    private final UsageRecorder usage;
    private final int runCeiling;

    ImportEnrichmentResearch(ProfileResearch profiles, CandidateService candidates, CompanyResearch companies,
                             TriageCompanyReadService companyReads, TriageCompanyService triage,
                             ImportEnrichmentItemStore store, FairUseGuard fairUse, UsageRecorder usage,
                             LightMoveProperties properties) {
        this.profiles = profiles;
        this.candidates = candidates;
        this.companies = companies;
        this.companyReads = companyReads;
        this.triage = triage;
        this.store = store;
        this.fairUse = fairUse;
        this.usage = usage;
        this.runCeiling = properties.spreadsheetImport().maxAutoEnrich();
    }

    ImportEnrichmentOutcome research(ClaimedEnrichmentItem item) {
        try {
            return item.kind() == ImportEnrichmentKind.PERSON ? researchPerson(item) : researchCompany(item);
        } catch (OverAllowance spent) {
            return ImportEnrichmentOutcome.OVER_ALLOWANCE;
        }
    }

    private ImportEnrichmentOutcome researchPerson(ClaimedEnrichmentItem item) {
        try {
            candidates.requireCandidate(item.workspaceId(), item.projectId(), item.targetId());
        } catch (ApiException removed) {
            return ImportEnrichmentOutcome.UNAVAILABLE;
        }
        ProfileResearchOutcome outcome = profiles.researchOrFetch(item.workspaceId(),
                LinkedInUrls.profileUrlOf(item.linkedinSlug()), () -> requirePaidRead(item));
        if (outcome instanceof ProfileResearchOutcome.Fetched || outcome instanceof ProfileResearchOutcome.NotFound) {
            recordPaidRead(item);
        }
        return switch (outcome) {
            case ProfileResearchOutcome.Refused refused -> ImportEnrichmentOutcome.REFUSED;
            case ProfileResearchOutcome.Unavailable unavailable -> ImportEnrichmentOutcome.UNAVAILABLE;
            case ProfileResearchOutcome.NotFound notFound -> ImportEnrichmentOutcome.NOT_FOUND;
            case ProfileResearchOutcome.OnFile onFile -> {
                candidates.applyResearch(item.projectId(), item.targetId(), onFile.research());
                yield ImportEnrichmentOutcome.ALREADY_RESEARCHED;
            }
            case ProfileResearchOutcome.Fetched fetched -> {
                candidates.applyResearch(item.projectId(), item.targetId(), fetched.research());
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

    private ImportEnrichmentOutcome researchCompany(ClaimedEnrichmentItem item) {
        TriageCompanyResponse company;
        try {
            company = companyReads.get(item.workspaceId(), item.projectId(), item.targetId());
        } catch (ApiException removed) {
            return ImportEnrichmentOutcome.UNAVAILABLE;
        }
        // The market's snapshot is never overwritten: that is what its Source badge promises.
        if (company.apolloAccountId() != null || !companies.isEnabled()) {
            return ImportEnrichmentOutcome.UNAVAILABLE;
        }
        boolean cached = !companies.heldRecordsOf(List.of(item.linkedinSlug())).isEmpty();
        if (!cached) {
            requirePaidRead(item);
        }
        Optional<CapturedCompanyDetails> found = companies.of(item.linkedinSlug());
        if (!cached) {
            recordPaidRead(item);
        }
        if (found.isEmpty()) {
            return ImportEnrichmentOutcome.NOT_FOUND;
        }
        triage.applyEnrichment(item.projectId(), item.targetId(), found.get());
        return cached ? ImportEnrichmentOutcome.ALREADY_RESEARCHED : ImportEnrichmentOutcome.RESEARCHED;
    }

    private void requirePaidRead(ClaimedEnrichmentItem item) {
        try {
            fairUse.check(item.workspaceId(), item.requestedBy(), UsageKind.PROFILE_ENRICH, 1);
        } catch (ApiException fairUseReachedOrTrialEnded) {
            throw new OverAllowance();
        }
    }

    private void recordPaidRead(ClaimedEnrichmentItem item) {
        usage.record(MeteredUse.of(item.workspaceId(), item.requestedBy(), item.projectId(),
                UsageKind.PROFILE_ENRICH, 1));
    }

    /** Thrown from the paid-read hook to stop the read; never leaves this class. */
    private static final class OverAllowance extends RuntimeException {
        OverAllowance() {
            super(null, null, false, false);
        }
    }
}
