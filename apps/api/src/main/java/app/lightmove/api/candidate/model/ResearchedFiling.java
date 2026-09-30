package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.CandidateSource;
import java.util.UUID;

/**
 * How a vendor search hit is filed: the door it came through, the deep enrichment it queues — none when
 * {@code enrichTrigger} is null — and the Find executives run that found it, when one did.
 */
public record ResearchedFiling(CandidateSource source, AiEnrichTrigger enrichTrigger, UUID runId) {

    public static ResearchedFiling ofSourcingRun(UUID runId) {
        return new ResearchedFiling(CandidateSource.AI_SOURCED, AiEnrichTrigger.SOURCING, runId);
    }

    /** No deep enrichment: a researcher asks for it from the drawer, one person at a time. */
    public static ResearchedFiling ofPeopleSearch() {
        return new ResearchedFiling(CandidateSource.PEOPLE_SEARCH, null, null);
    }
}
