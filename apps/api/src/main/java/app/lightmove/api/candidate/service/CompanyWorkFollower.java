package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.triagecompany.model.TriageCompanyWorked;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company someone chose to add or edit is no longer only filed for its executives, so the plugin's Undo of one of
 * them must leave it. Entity by entity, so each row's version guards a racing save.
 */
@Component
@RequiredArgsConstructor
class CompanyWorkFollower {

    private final CandidateRepository candidates;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void follow(TriageCompanyWorked worked) {
        List<Candidate> mapped = candidates.findByProjectIdAndTriageCompanyIdIn(worked.projectId(),
                List.of(worked.triageCompanyId()));
        mapped.forEach(Candidate::keepCompany);
    }
}
