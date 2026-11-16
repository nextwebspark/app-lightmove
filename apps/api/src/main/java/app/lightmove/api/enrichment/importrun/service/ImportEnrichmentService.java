package app.lightmove.api.enrichment.importrun.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.enrichment.candidate.service.LinkedInProfileEnricher;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import app.lightmove.api.enrichment.importrun.model.EnrichmentOffer;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentProgress;
import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentTarget;
import app.lightmove.api.enrichment.importrun.model.QueuedEnrichment;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * What a spreadsheet import asks of enrichment: what is on offer, whether this file may be enriched at all — decided
 * before any row is written — and the queueing of what it filed. The research itself is the worker's.
 */
@Service
public class ImportEnrichmentService {

    public static final String ENRICH_FIELD = "enrich";

    private final LinkedInProfileEnricher profiles;
    private final CompanyResearch companies;
    private final FairUseGuard fairUse;
    private final ImportEnrichmentItemStore store;
    private final int maxAutoEnrich;

    public ImportEnrichmentService(LinkedInProfileEnricher profiles, CompanyResearch companies, FairUseGuard fairUse,
                                   ImportEnrichmentItemStore store, LightMoveProperties properties) {
        this.profiles = profiles;
        this.companies = companies;
        this.fairUse = fairUse;
        this.store = store;
        this.maxAutoEnrich = properties.spreadsheetImport().maxAutoEnrich();
    }

    public EnrichmentOffer offerFor(UUID workspaceId) {
        boolean profilesOffered = profiles.isOffered();
        boolean companiesOffered = companies.isEnabled();
        OptionalLong left = profilesOffered || companiesOffered
                ? fairUse.unitsLeft(workspaceId, UsageKind.PROFILE_ENRICH)
                : OptionalLong.empty();
        return new EnrichmentOffer(profilesOffered, companiesOffered, maxAutoEnrich,
                left.isPresent() ? left.getAsLong() : null);
    }

    /**
     * Refuses, before anything is written, a file with more links than one import enriches, and one this month's fair
     * use cannot cover. The count is every distinct link — an upper bound, since a person already researched costs
     * nothing — so the check never lets through more than the allowance holds.
     */
    public void requireEnrichable(UUID workspaceId, UUID userId, int profileLinks, int companyLinks) {
        EnrichmentOffer offer = offerFor(workspaceId);
        int links = (offer.profilesOffered() ? profileLinks : 0) + (offer.companiesOffered() ? companyLinks : 0);
        if (links == 0) {
            return;
        }
        if (links > maxAutoEnrich) {
            // Both numbers are ours — a count we made and a configured limit — so safe to interpolate.
            throw ApiException.withField(ErrorCode.IMPORT_ENRICHMENT_TOO_LARGE, ENRICH_FIELD,
                    "This file has %,d LinkedIn links, and an import enriches at most %,d. Untick enrichment to import it."
                            .formatted(links, maxAutoEnrich));
        }
        fairUse.check(workspaceId, userId, UsageKind.PROFILE_ENRICH, links);
    }

    /** Only the kinds a provider answers for are queued; a person or company named twice is queued once. */
    public QueuedEnrichment queue(UUID workspaceId, UUID projectId, UUID requestedBy,
                                  Collection<ImportEnrichmentTarget> targets) {
        EnrichmentOffer offer = offerFor(workspaceId);
        List<ImportEnrichmentTarget> offered = targets.stream()
                .filter(target -> target.kind() == ImportEnrichmentKind.PERSON
                        ? offer.profilesOffered()
                        : offer.companiesOffered())
                .distinct()
                .toList();
        UUID runId = UUID.randomUUID();
        if (!offered.isEmpty()) {
            store.queue(workspaceId, projectId, runId, requestedBy, offered);
        }
        int people = (int) offered.stream().filter(target -> target.kind() == ImportEnrichmentKind.PERSON).count();
        return new QueuedEnrichment(runId, people, offered.size() - people);
    }

    public ImportEnrichmentProgress progressOf(UUID workspaceId, UUID projectId, UUID runId) {
        if (!store.runExists(workspaceId, projectId, runId)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return store.progressOf(workspaceId, projectId, runId);
    }
}
