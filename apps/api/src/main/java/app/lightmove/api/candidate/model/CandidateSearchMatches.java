package app.lightmove.api.candidate.model;

import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.CompanySearchMatches;
import app.lightmove.api.triagecompany.model.StageSearchMatches;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What the mandate's one search box matched: executives on their own, and companies on theirs. An executive is
 * kept when they match or their company does, so a matching company keeps everyone at it. {@code stage}, when not
 * null, is the one stage the executives were matched among.
 */
public record CandidateSearchMatches(TriageCompanyStatus stage, Set<UUID> candidateIds,
                                     Set<UUID> candidateCompanyIds, CompanySearchMatches companies) {

    public CandidateSearchMatches {
        candidateIds = Set.copyOf(candidateIds);
        candidateCompanyIds = candidateCompanyIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> companyIds() {
        return companies.ids();
    }

    public boolean keeps(UUID candidateId, UUID triageCompanyId) {
        return candidateIds.contains(candidateId) || triageCompanyId != null && companyIds().contains(triageCompanyId);
    }

    /** The same matches as the stage read narrows by, so it need not scan for them again. */
    public StageSearchMatches forStageRead() {
        if (stage == null) {
            throw new IllegalStateException("Matched across every stage, not one");
        }
        return new StageSearchMatches(stage, companies, candidateCompanyIds);
    }
}
