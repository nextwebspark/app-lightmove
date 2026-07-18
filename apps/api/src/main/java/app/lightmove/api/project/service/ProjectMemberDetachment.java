package app.lightmove.api.project.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.repository.ProjectMemberRepository;
import app.lightmove.api.workspace.service.MemberDetachment;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What projects require of a workspace-member removal: no live mandate may lose its <i>only</i>
 * project admin, and the member's seats go with them. Leads are plural and non-structural, so a LEAD
 * seat never blocks removal. Deliberately, a sole-admin seat on a DELIVERED/CLOSED mandate does not
 * block either — blocking on closed work would make removal impossible over time.
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberDetachment implements MemberDetachment {

    private static final List<ProjectStage> DONE_STAGES =
            List.of(ProjectStage.DELIVERED, ProjectStage.CLOSED);

    private final ProjectMemberRepository seats;

    @Override
    public void assertRemovable(UUID memberId) {
        if (seats.countSoleAdminSeatsExcludingStages(memberId, DONE_STAGES) > 0) {
            throw ApiException.of(ErrorCode.MEMBER_LEADS_PROJECTS);
        }
    }

    @Override
    public void detach(UUID memberId) {
        seats.deleteByMemberId(memberId);
    }
}
