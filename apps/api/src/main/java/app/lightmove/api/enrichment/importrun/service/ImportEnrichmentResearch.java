package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.candidate.service.LookedUpResearch;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.candidate.service.LinkedInProfileEnricher;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Researches one imported person or company and files what was found through the owning service, which fills only
 * what nobody recorded — the file always wins. Written apart from the worker so a per-person Enrich can call it.
 * Not transactional: no connection is held across a vendor read.
 */
@Service
@RequiredArgsConstructor
public class ImportEnrichmentResearch {

    private final LookedUpResearch lookedUp;
    private final LinkedInProfileEnricher profiles;
    private final CandidateService candidates;
    private final CompanyResearch companies;
    private final TriageCompanyReadService companyReads;
    private final TriageCompanyService triage;
    private final ImportEnrichmentItemStore store;
    private final UsageRecorder usage;

    ImportEnrichmentOutcome research(ClaimedEnrichmentItem item) {
        return item.kind() == ImportEnrichmentKind.PERSON ? researchPerson(item) : researchCompany(item);
    }

    private ImportEnrichmentOutcome researchPerson(ClaimedEnrichmentItem item) {
        try {
            candidates.requireCandidate(item.workspaceId(), item.projectId(), item.targetId());
        } catch (ApiException removed) {
            return ImportEnrichmentOutcome.UNAVAILABLE;
        }
        String profileUrl = LinkedInUrls.profileUrlOf(item.linkedinSlug());
        try {
            lookedUp.refuseUnresearchable(item.workspaceId(), profileUrl);
        } catch (ApiException suppressedOrRestricted) {
            return ImportEnrichmentOutcome.REFUSED;
        }

        Optional<EnrichedProfile> onFile = lookedUp.researchOnFile(item.workspaceId(), profileUrl);
        if (onFile.isPresent()) {
            candidates.applyResearch(item.projectId(), item.targetId(), onFile.get());
            return ImportEnrichmentOutcome.ALREADY_RESEARCHED;
        }
        if (!profiles.isOffered()) {
            return ImportEnrichmentOutcome.UNAVAILABLE;
        }

        Optional<EnrichedProfile> found = profiles.fetch(profileUrl);
        usage.record(MeteredUse.of(item.workspaceId(), item.requestedBy(), item.projectId(),
                UsageKind.PROFILE_ENRICH, 1));
        if (found.isEmpty()) {
            return ImportEnrichmentOutcome.NOT_FOUND;
        }
        lookedUp.remember(item.workspaceId(), profileUrl, found.get());
        candidates.applyResearch(item.projectId(), item.targetId(), found.get());
        queueEmployerPage(item, found.get());
        return ImportEnrichmentOutcome.RESEARCHED;
    }

    /**
     * The row's company had no LinkedIn page in the file, and the person's research names it with one: that page is
     * researched too. Only when the names agree — a researched employer the file did not state is not the file's.
     */
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
                    employerSlug));
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
        Optional<CapturedCompanyDetails> found = companies.of(item.linkedinSlug());
        if (!cached) {
            usage.record(MeteredUse.of(item.workspaceId(), item.requestedBy(), item.projectId(),
                    UsageKind.PROFILE_ENRICH, 1));
        }
        if (found.isEmpty()) {
            return ImportEnrichmentOutcome.NOT_FOUND;
        }
        triage.applyEnrichment(item.projectId(), item.targetId(), found.get());
        return cached ? ImportEnrichmentOutcome.ALREADY_RESEARCHED : ImportEnrichmentOutcome.RESEARCHED;
    }
}
