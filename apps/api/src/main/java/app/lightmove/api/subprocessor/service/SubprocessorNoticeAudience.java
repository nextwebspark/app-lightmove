package app.lightmove.api.subprocessor.service;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Who a notice goes to: the active holders of {@code WORKSPACE_MANAGE} in every workspace that is not deleted, an
 * account that can no longer sign in left out. {@code app_lm_workspace} and membership carry no policy.
 */
@Component
@RequiredArgsConstructor
public class SubprocessorNoticeAudience {

    private final JdbcClient jdbc;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;

    public List<UUID> liveWorkspaceIds() {
        return jdbc.sql("SELECT id FROM app_lm_workspace WHERE status <> 'DELETED' AND deleted_at IS NULL ORDER BY id")
                .query(UUID.class)
                .list();
    }

    /** Empty for a workspace deleted since, whose admins are owed nothing more. */
    public Optional<String> liveWorkspaceName(UUID workspaceId) {
        return jdbc.sql("SELECT name FROM app_lm_workspace WHERE id = :id AND status <> 'DELETED' AND deleted_at IS NULL")
                .param("id", workspaceId)
                .query(String.class)
                .optional();
    }

    /** Any workspace's name, deleted or not: the console names who objected even after they left. */
    public Optional<String> workspaceName(UUID workspaceId) {
        return jdbc.sql("SELECT name FROM app_lm_workspace WHERE id = :id")
                .param("id", workspaceId)
                .query(String.class)
                .optional();
    }

    public List<User> adminsOf(UUID workspaceId) {
        List<UUID> admins = members.findUserIdsHoldingAction(workspaceId, WorkspaceAction.WORKSPACE_MANAGE.name(),
                MemberStatus.ACTIVE);
        return users.findAllById(admins).stream()
                .filter(user -> user.getStatus().canAuthenticate())
                .toList();
    }
}
