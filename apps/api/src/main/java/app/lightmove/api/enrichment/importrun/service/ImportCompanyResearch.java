package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.enrichment.company.model.CompanyRead;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentOutcome;
import app.lightmove.api.enrichment.importrun.model.ClaimedEnrichmentItem;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * One imported company's page, filed by {@code TriageCompanyService.applyEnrichment}. Metered only when
 * {@link CompanyResearch#read} says the vendor was asked: a fresh cached miss costs nothing, a stale record is bought.
 */
@Service
@RequiredArgsConstructor
class ImportCompanyResearch {

    private final CompanyResearch companies;
    private final TriageCompanyReadService companyReads;
    private final TriageCompanyService triage;
    private final ImportEnrichmentAllowance allowance;

    ImportEnrichmentOutcome research(ClaimedEnrichmentItem item) {
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
        CompanyRead read = companies.read(item.linkedinSlug(), () -> allowance.requirePaidRead(item));
        if (read.bought()) {
            allowance.recordPaidRead(item);
        }
        Optional<CapturedCompanyDetails> found = read.details();
        if (found.isEmpty()) {
            return ImportEnrichmentOutcome.NOT_FOUND;
        }
        triage.applyEnrichment(item.projectId(), item.targetId(), found.get());
        return read.bought() ? ImportEnrichmentOutcome.RESEARCHED : ImportEnrichmentOutcome.ALREADY_RESEARCHED;
    }
}
