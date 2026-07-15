package app.lightmove.api.workspace.application;

import app.lightmove.api.auth.api.dto.AuthDtos.JoinableWorkspace;
import app.lightmove.api.auth.infrastructure.UserRepository;
import app.lightmove.api.workspace.domain.MemberStatus;
import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import app.lightmove.api.workspace.infrastructure.WorkspaceMemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Describes a workspace to somebody who is <b>not in it</b>.
 *
 * <p>Its own class because that audience is the whole point, and the temptation to reuse a richer
 * workspace DTO here would be a slow leak. Everything below is chosen to be the minimum that lets
 * someone recognise their own firm — its name, who runs it, roughly how many people — and nothing about
 * the mandates, clients or candidates inside it. A stranger on the domain sees a name and a headcount.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceSummaries {

    private final WorkspaceMemberRepository members;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<JoinableWorkspace> joinable(List<Workspace> workspaces) {
        return workspaces.stream().map(this::summarise).toList();
    }

    private JoinableWorkspace summarise(Workspace workspace) {
        List<WorkspaceMember> active = members.findByWorkspaceIdAndStatus(workspace.getId(), MemberStatus.ACTIVE);

        return new JoinableWorkspace(
                workspace.getId(),
                workspace.getName(),
                workspace.getLogoMark(),
                active.size(),
                // Naming an admin is what makes this recognisable — "the workspace Yara runs" is how a
                // new colleague identifies their own team. It is also who will decide on their request.
                firstAdminName(active));
    }

    private String firstAdminName(List<WorkspaceMember> active) {
        return active.stream()
                .filter(member -> member.getRole() == WorkspaceRole.ADMIN)
                .findFirst()
                .map(WorkspaceMember::getUserId)
                .flatMap(this::nameOf)
                .orElse(null);
    }

    private java.util.Optional<String> nameOf(UUID userId) {
        return users.findById(userId).map(user -> user.getFullName());
    }
}
