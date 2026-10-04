package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which of a user's workspaces a session is in — the one rule every sign-in path shares: preferred,
 * else last chosen, else first joined, else none. An ended membership is skipped at every step.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceSelection {

    private final WorkspaceMemberRepository members;

    @Transactional(readOnly = true)
    public Optional<WorkspaceMember> select(User user, UUID preferredWorkspaceId) {
        return membershipIn(user.getId(), preferredWorkspaceId)
                .or(() -> membershipIn(user.getId(), user.getLastWorkspaceId()))
                .or(() -> all(user.getId()).stream().findFirst());
    }

    /** The user's active membership in exactly this workspace, with no fallback — a miss is a miss. */
    @Transactional(readOnly = true)
    public Optional<WorkspaceMember> membershipIn(UUID userId, UUID workspaceId) {
        return workspaceId == null ? Optional.empty()
                : members.findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE);
    }

    /** Every workspace the user is in, oldest first — what the switcher lists. */
    @Transactional(readOnly = true)
    public List<WorkspaceMember> all(UUID userId) {
        return members.findAllByUserIdAndStatusOrderByJoinedAtAsc(userId, MemberStatus.ACTIVE);
    }

    /** Where a fresh sign-in opens, remembered as the user's choice. Null for a user in no workspace. */
    @Transactional
    public WorkspaceMember signIn(User user) {
        WorkspaceMember membership = select(user, null).orElse(null);
        remember(user, membership);
        return membership;
    }

    /**
     * An explicit choice — sign-in, switch, create, accept — and never a refresh, or two browsers in two
     * workspaces would overwrite each other's every fifteen minutes.
     */
    public void remember(User user, WorkspaceMember membership) {
        if (membership == null || membership.getWorkspaceId().equals(user.getLastWorkspaceId())) {
            return;
        }
        user.rememberWorkspace(membership.getWorkspaceId());
    }
}
