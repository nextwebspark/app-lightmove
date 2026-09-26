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
        Optional<WorkspaceMember> preferred = membershipIn(user.getId(), preferredWorkspaceId);
        if (preferred.isPresent()) {
            return preferred;
        }
        Optional<WorkspaceMember> lastChosen = membershipIn(user.getId(), user.getLastWorkspaceId());
        if (lastChosen.isPresent()) {
            return lastChosen;
        }
        return all(user.getId()).stream().findFirst();
    }

    /** The user's active membership in exactly this workspace, with no fallback — a miss is a miss. */
    @Transactional(readOnly = true)
    public Optional<WorkspaceMember> membershipIn(UUID userId, UUID workspaceId) {
        if (workspaceId == null) {
            return Optional.empty();
        }
        return members.findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE);
    }

    /** Every workspace the user is in, oldest first — what the switcher lists. */
    @Transactional(readOnly = true)
    public List<WorkspaceMember> all(UUID userId) {
        return members.findAllByUserIdAndStatusOrderByJoinedAtAsc(userId, MemberStatus.ACTIVE);
    }

    /**
     * Records an explicit choice, so the next sign-in opens there. Called on sign-in, switch, create
     * and accept — and deliberately <b>not</b> on refresh, or two browsers open in two workspaces
     * would overwrite each other's choice every fifteen minutes.
     */
    public void remember(User user, WorkspaceMember membership) {
        if (membership == null || membership.getWorkspaceId().equals(user.getLastWorkspaceId())) {
            return;
        }
        user.rememberWorkspace(membership.getWorkspaceId());
    }
}
