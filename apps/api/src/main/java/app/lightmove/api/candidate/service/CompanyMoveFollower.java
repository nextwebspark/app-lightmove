package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.triagecompany.model.TriageCompanyMoved;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moving a company moves everyone mapped at it, in the company's own transaction; moving one executive is
 * {@code CandidateService.changeTriageStatus} and leaves the company and its other people where they stand (#903).
 */
@Component
@RequiredArgsConstructor
class CompanyMoveFollower {

    private final CandidateRepository candidates;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void follow(TriageCompanyMoved moved) {
        candidates.moveEveryoneAtCompanyTo(moved.projectId(), moved.triageCompanyId(), moved.status());
    }
}
