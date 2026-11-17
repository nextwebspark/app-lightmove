package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.triagecompany.model.TriageCompanyRenamed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A renamed company's people take its new name in the company's own transaction. The snapshot is what the drawer,
 * an email's company and the AI dossier read, so before this they went on naming the company as it was typed first.
 */
@Component
@RequiredArgsConstructor
class CompanyRenameFollower {

    private final CandidateRepository candidates;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void follow(TriageCompanyRenamed renamed) {
        List<Candidate> mapped = candidates.findByProjectIdAndTriageCompanyIdIn(renamed.projectId(),
                List.of(renamed.triageCompanyId()));
        mapped.forEach(candidate -> candidate.followCompanyRename(renamed.companyName()));
    }
}
