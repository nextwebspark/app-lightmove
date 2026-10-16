package app.uncava.api.project.service;

import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.core.security.rbac.ProjectRole;
import app.uncava.api.project.constant.ProjectStage;
import app.uncava.api.project.model.ProjectSeatVacated;
import app.uncava.api.project.repository.ProjectMemberRepository;
import app.uncava.api.workspace.repository.WorkspaceMemberRepository;
import app.uncava.api.workspace.service.MemberDetachment;
import java.util.List;
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

    @Override
    public void assertRemovable(UUID memberId) {
        if (seats.countSoleLeadSeatsExcludingStages(memberId, DONE_STAGES) > 0) {
            throw ApiException.of(ErrorCode.MEMBER_LEADS_PROJECTS);
        }
    }

    @Override
    public void detach(UUID memberId) {
        members.findById(memberId).ifPresent(member -> seats.findByMemberId(memberId).stream()
                .filter(seat -> seat.getRoles().stream().anyMatch(role -> !role.is(ProjectRole.CLIENT)))
                .forEach(seat -> events.publishEvent(new ProjectSeatVacated(member.getWorkspaceId(),
                        seat.getProjectId(), member.getUserId(), null))));
        seats.deleteByMemberId(memberId);
    }
}
