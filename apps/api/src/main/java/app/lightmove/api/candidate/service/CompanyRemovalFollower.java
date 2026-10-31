package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.triagecompany.model.TriageCompanyRemoved;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company leaving a mandate takes everyone mapped at it, on every stage, in the company's own transaction, so nobody
 * is left behind with a name and no company. Entity by entity, so each row's version guards a racing drawer save and
 * each person gets their timeline line.
 */
@Component
@RequiredArgsConstructor
class CompanyRemovalFollower {

    private final CandidateRepository candidates;
    private final CandidateService mandates;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void follow(TriageCompanyRemoved removed) {
        candidates.findByProjectIdAndTriageCompanyIdIn(removed.projectId(), List.of(removed.triageCompanyId()))
                .forEach(candidate -> mandates.removeWithTheirCompany(candidate, removed.removedBy(),
                        removed.workspaceId()));
    }
}
