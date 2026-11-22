package app.lightmove.api.startpage.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.StartPageResponse;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.dashboard.service.DashboardService;
import app.lightmove.api.startpage.constant.PositionSection;
import app.lightmove.api.startpage.constant.StartPageKind;
import app.lightmove.api.startpage.dto.SaveStartPageRequest;
import app.lightmove.api.startpage.model.StartPage;
import app.lightmove.api.startpage.model.StartPageTarget;
import app.lightmove.api.startpage.repository.StartPageRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where a member's Uncava opens in a workspace. The saved page is checked against the member's access on every read
 * as well as on save: a seat or a shared dashboard can be lost after it was chosen.
 */
@Service
@RequiredArgsConstructor
public class StartPageService {

    private final StartPageRepository startPages;
    private final WorkspaceAccess workspaceAccess;
    private final ProjectAccess projectAccess;
    private final DashboardService dashboards;

    @Transactional(readOnly = true)
    public StartPageResponse read(UUID userId, UUID workspaceId) {
        return read(workspaceAccess.requireActiveMember(userId, workspaceId));
    }

    /** For an active membership already in hand, as every auth response holds one. */
    @Transactional(readOnly = true)
    public StartPageResponse read(WorkspaceMember member) {
        return startPages.findByWorkspaceIdAndUserId(member.getWorkspaceId(), member.getUserId())
                .map(StartPage::target)
                .map(target -> toResponse(target, isOpenTo(member, target)))
                .orElse(StartPageResponse.HOME);
    }

    @Transactional
    public StartPageResponse save(UUID userId, UUID workspaceId, SaveStartPageRequest request) {
        StartPageTarget target = targetOf(request);
        if (target.kind() == StartPageKind.HOME) {
            startPages.deleteByMember(workspaceId, userId);
            return StartPageResponse.HOME;
        }
        if (!isOpenTo(workspaceAccess.requireActiveMember(userId, workspaceId), target)) {
            throw new ApiException(ErrorCode.START_PAGE_UNAVAILABLE, "Start page " + target + " is not open to the member");
        }
        startPages.upsert(workspaceId, userId, target.kind().value(),
                target.dashboardId() == null ? null : target.dashboardId().toString(),
                target.projectId() == null ? null : target.projectId().toString(),
                target.section() == null ? null : target.section().value());
        return toResponse(target, true);
    }

    private static StartPageTarget targetOf(SaveStartPageRequest request) {
        StartPageKind kind = ApiValueEnum.require(StartPageKind.class, request.kind(), "start page");
        return switch (kind) {
            case DASHBOARD -> new StartPageTarget(kind, required(request.dashboardId(), "dashboard"), null, null);
            case POSITION -> new StartPageTarget(kind, null, required(request.projectId(), "position"),
                    ApiValueEnum.require(PositionSection.class, request.section(), "position tab"));
            default -> new StartPageTarget(kind, null, null, null);
        };
    }

    private static UUID required(UUID id, String label) {
        if (id == null) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Choose a " + label, "No " + label + " named");
        }
        return id;
    }

    private boolean isOpenTo(WorkspaceMember member, StartPageTarget target) {
        if (target.kind().isStaffOnly() && !isStaff(member)) {
            return false;
        }
        return switch (target.kind()) {
            case DASHBOARD -> dashboards.isVisibleOnWorkspace(member.getUserId(), member.getWorkspaceId(),
                    target.dashboardId());
            case POSITION -> projectAccess.allows(member, target.projectId(),
                    target.section() == PositionSection.OUTREACH ? ProjectAction.WORK_EXECUTE : ProjectAction.WORK_VIEW);
            default -> true;
        };
    }

    private static boolean isStaff(WorkspaceMember member) {
        return WorkspaceRole.isStaff(member.getRoles().stream().map(Role::getName).map(WorkspaceRole::valueOf).toList());
    }

    private static StartPageResponse toResponse(StartPageTarget target, boolean available) {
        return new StartPageResponse(target.kind().value(), target.dashboardId(), target.projectId(),
                target.section() == null ? null : target.section().value(), available);
    }
}
