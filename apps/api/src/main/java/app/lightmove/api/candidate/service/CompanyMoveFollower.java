package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompanyMoved;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company's move carries its people in the company's own transaction: those still where it stood, or exactly the
 * ones named. Someone moved on their own stays where they were put (#903). Entity by entity rather than a bulk update,
 * so each row's version guards a drawer save racing the move, and each person gets their timeline line.
 */
@Component
@RequiredArgsConstructor
class CompanyMoveFollower {

    private final CandidateRepository candidates;
    private final PersonActivityRecorder activity;
    private final ProjectStreamPublisher stream;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void follow(TriageCompanyMoved moved) {
        List<Candidate> carried = moved.executiveIds() == null
                ? candidates.findByProjectIdAndTriageCompanyIdAndTriageStatus(moved.projectId(),
                        moved.triageCompanyId(), moved.from())
                : moved.executiveIds().isEmpty() ? List.of()
                : candidates.findByProjectIdAndTriageCompanyIdAndIdIn(moved.projectId(), moved.triageCompanyId(),
                        moved.executiveIds());
        boolean anyMoved = false;
        for (Candidate candidate : carried) {
            TriageCompanyStatus from = candidate.getTriageStatus();
            if (candidate.moveToStage(moved.to())) {
                activity.record(candidate, moved.movedBy(), PersonActivityKind.TRIAGE_STATUS_CHANGED,
                        PersonActivityDetails.triageStatusChange(from, moved.to(), true));
                anyMoved = true;
            }
        }
        if (anyMoved) {
            stream.publish(moved.projectId(), ProjectStreamKind.CANDIDATE_MOVED);
        }
    }
}
