package app.lightmove.api.project.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectRole;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.model.ProjectSeatVacated;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.workspace.model.LedAlonePosition;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.service.MemberDetachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Member removal: no live mandate may lose its only lead, and the seats go too. A sole lead on a
 * DELIVERED/CLOSED or archived mandate deliberately does not block, or removal would become impossible over
 * time; an admin seats a new lead to restore one.
 * Each staff seat's going is announced first, so the work it held can be handed on in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberDetachment implements MemberDetachment {

    private static final List<ProjectStage> DONE_STAGES =
            List.of(ProjectStage.DELIVERED, ProjectStage.CLOSED);

    private final ProjectMemberRepository seats;
    private final WorkspaceMemberRepository members;
    private final ApplicationEventPublisher events;
    private final ClientRepresentativeRepository representatives;

    @Override
    public void assertRemovable(UUID memberId) {
        if (seats.countSoleLeadSeatsExcludingStages(memberId, DONE_STAGES) > 0) {
            throw ApiException.of(ErrorCode.MEMBER_LEADS_PROJECTS);
        }
    }

    @Override
    public List<LedAlonePosition> positionsLedAloneBy(UUID memberId) {
        return seats.findSoleLeadPositionsExcludingStages(memberId, DONE_STAGES);
    }

    @Override
    public void detach(UUID memberId) {
        Optional<WorkspaceMember> member = members.findById(memberId);
        member.ifPresent(leaving -> seats.findByMemberId(memberId).stream()
                .filter(seat -> seat.getRoles().stream().anyMatch(role -> !role.is(ProjectRole.CLIENT)))
                .forEach(seat -> events.publishEvent(new ProjectSeatVacated(leaving.getWorkspaceId(),
                        seat.getProjectId(), leaving.getUserId(), null))));
        seats.deleteByMemberId(memberId);
        // A contact whose membership ended is no longer one: left ACTIVE, the record refused to re-invite them
        // ("already one of its representatives") and the mandate screens could neither attach nor detach them.
        // No audit line of its own: the MEMBER_REMOVED that caused it is the record.
        member.ifPresent(leaving -> representatives
                .findByWorkspaceIdAndUserIdAndStatus(leaving.getWorkspaceId(), leaving.getUserId(),
                        ClientRepStatus.ACTIVE)
                .forEach(ClientRepresentative::revoke));
    }
}
