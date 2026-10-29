package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateSearchMatches;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.SearchMatchedCandidate;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.core.text.model.SearchText;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mandate's one search rule as people reads apply it — the candidates list, the talent map, the export and
 * the Pipeline. Notes and tags are matched only for a staff {@code audience}, which {@code ProjectAccess} decides
 * from {@code WORK_EXECUTE}, never a request.
 */
@Service
@RequiredArgsConstructor
public class CandidateSearchService {

    private final CandidateRepository candidates;
    private final TriageCompanyReadService companies;

    @Transactional(readOnly = true)
    public CandidateSearchMatches matchesOf(UUID workspaceId, UUID projectId, SearchText search,
                                            ProjectAudience audience) {
        return matchesOf(workspaceId, projectId, null, search, audience);
    }

    /** {@code stage}, when not null, matches only the executives listed there; companies match at any stage. */
    @Transactional(readOnly = true)
    public CandidateSearchMatches matchesOf(UUID workspaceId, UUID projectId, TriageCompanyStatus stage,
                                            SearchText search, ProjectAudience audience) {
        List<SearchMatchedCandidate> matched = candidates.findSearchMatches(projectId, stage, search, audience);
        return new CandidateSearchMatches(stage,
                matched.stream().map(SearchMatchedCandidate::getId).collect(Collectors.toSet()),
                matched.stream().map(SearchMatchedCandidate::getTriageCompanyId).collect(Collectors.toSet()),
                companies.companiesMatchingSearch(workspaceId, projectId, search, audience));
    }
}
