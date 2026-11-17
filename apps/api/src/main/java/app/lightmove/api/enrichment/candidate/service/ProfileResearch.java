package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.LookedUpResearch;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.enrichment.candidate.model.ProfileResearchOutcome;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The one path to a profile's research, shared by the Add form's lookup and an import's enrichment, so a safeguard
 * added here reaches both: who may be researched, what is already held, then the paid read. Not transactional: no
 * connection is held through the vendor call.
 */
@Service
@RequiredArgsConstructor
public class ProfileResearch {

    private final LookedUpResearch lookedUp;
    private final LinkedInProfileEnricher enricher;

    /**
     * {@code beforePaidRead} runs only when the vendor is about to be asked — a budget or an allowance — and stops
     * the read by throwing. A vendor failure is thrown to the caller, which decides what it means.
     */
    public ProfileResearchOutcome researchOrFetch(UUID workspaceId, String profileUrl, Runnable beforePaidRead) {
        try {
            lookedUp.refuseUnresearchable(workspaceId, profileUrl);
        } catch (ApiException suppressedOrRestricted) {
            return new ProfileResearchOutcome.Refused(suppressedOrRestricted);
        }
        Optional<EnrichedProfile> onFile = lookedUp.researchOnFile(workspaceId, profileUrl);
        if (onFile.isPresent()) {
            return new ProfileResearchOutcome.OnFile(onFile.get());
        }
        if (!enricher.isOffered()) {
            return new ProfileResearchOutcome.Unavailable();
        }
        beforePaidRead.run();
        Optional<EnrichedProfile> fetched = enricher.fetch(profileUrl);
        if (fetched.isEmpty()) {
            return new ProfileResearchOutcome.NotFound();
        }
        lookedUp.remember(workspaceId, profileUrl, fetched.get());
        return new ProfileResearchOutcome.Fetched(fetched.get());
    }
}
