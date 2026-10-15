package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.constant.CandidateSource;
import java.util.UUID;

/**
 * How a vendor search hit is filed: the door it came through, the automatic AI run it queues — none when
 * {@code enrichTrigger} is null — the Find executives run that found it, and the employer to capture
 * with it, when there is one.
 */
public record ResearchedFiling(CandidateSource source, AiEnrichTrigger enrichTrigger, UUID runId,
                               ResearchedEmployer employer) {

    public static ResearchedFiling ofSourcingRun(UUID runId) {
        return new ResearchedFiling(CandidateSource.AI_SOURCED, AiEnrichTrigger.SOURCING, runId, null);
    }

    public static ResearchedFiling ofPeopleSearch(ResearchedEmployer employer) {
        return new ResearchedFiling(CandidateSource.PEOPLE_SEARCH, AiEnrichTrigger.PEOPLE_SEARCH, null, employer);
    }
}
