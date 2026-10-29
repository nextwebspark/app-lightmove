package app.lightmove.api.candidate.model;

import java.util.Set;
import java.util.UUID;

/**
 * What the mandate's one search box matched: executives on their own, and companies on theirs. An executive is
 * kept when they match or their company does, so a matching company keeps everyone at it.
 */
public record CandidateSearchMatches(Set<UUID> candidateIds, Set<UUID> companyIds) {

    public CandidateSearchMatches {
        candidateIds = Set.copyOf(candidateIds);
        companyIds = Set.copyOf(companyIds);
    }

    public boolean keeps(UUID candidateId, UUID triageCompanyId) {
        return candidateIds.contains(candidateId) || triageCompanyId != null && companyIds.contains(triageCompanyId);
    }
}
