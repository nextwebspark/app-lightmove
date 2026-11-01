package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Takes someone off a mandate inside the caller's transaction; the person stays with the workspace. Deliberately not
 * transactional itself, so its package-private calls never depend on a proxy.
 */
@Component
@RequiredArgsConstructor
class CandidateRemoval {

    private final CandidateRepository candidates;
    private final PersonActivityRecorder activity;
    private final AuditService audit;

    void unmap(Candidate candidate, UUID userId) {
        // The person stays with the workspace — their notes, contacts and history are the point of
        // keeping them — and the line saying which mandate let them go is written before the row goes.
        activity.record(candidate, userId, PersonActivityKind.UNMAPPED);
        candidates.delete(candidate);
    }

    /** Someone mapped at a company leaving the mandate with it ({@link CompanyRemovalFollower}). */
    void unmapWithTheirCompany(Candidate candidate, UUID removedBy, UUID workspaceId) {
        unmap(candidate, removedBy);
        recordRemoved(audit.event(ProjectEventType.CANDIDATE_REMOVED).actor(removedBy).workspace(workspaceId)
                .target(AuditService.PROJECT_TARGET, candidate.getProjectId()), candidate);
    }

    static void recordRemoved(AuditService.Builder event, Candidate candidate) {
        // The name is recorded because the row carrying it is about to stop existing, and an audit
        // entry naming only an unresolvable id answers no question later.
        event.detail("candidateId", candidate.getId().toString())
                .detail("fullName", candidate.getPerson().getFullName())
                .record();
    }
}
