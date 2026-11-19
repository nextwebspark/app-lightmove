package app.lightmove.api.outreach.repository;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.model.OutreachSequence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OutreachSequenceRepository extends JpaRepository<OutreachSequence, UUID> {

    List<OutreachSequence> findByWorkspaceIdAndProjectIdOrderByCreatedAtAsc(UUID workspaceId, UUID projectId);

    Optional<OutreachSequence> findByIdAndWorkspaceIdAndProjectId(UUID id, UUID workspaceId, UUID projectId);

    default OutreachSequence requireInProject(UUID id, UUID workspaceId, UUID projectId) {
        return findByIdAndWorkspaceIdAndProjectId(id, workspaceId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OutreachSequence s where s.id = :id and s.workspaceId = :workspaceId "
            + "and s.projectId = :projectId")
    Optional<OutreachSequence> findForChange(UUID id, UUID workspaceId, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from OutreachSequence s where s.id = :id and s.workspaceId = :workspaceId "
            + "and s.projectId = :projectId")
    Optional<OutreachSequence> findForEnrolment(UUID id, UUID workspaceId, UUID projectId);

    /**
     * A sequence about to change its steps or be deleted, locked against a Start: the check that nobody is on it
     * and the change itself must see the same enrolments.
     */
    default OutreachSequence requireForChange(UUID id, UUID workspaceId, UUID projectId) {
        return findForChange(id, workspaceId, projectId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** A sequence people are being put on, held so no edit changes its steps until they are. */
    default OutreachSequence requireForEnrolment(UUID id, UUID workspaceId, UUID projectId) {
        return findForEnrolment(id, workspaceId, projectId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
