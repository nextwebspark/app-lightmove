package app.lightmove.api.workspace.repository;

import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceRole;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    /**
     * The user's workspace. Singular: a user has at most one <i>active</i> membership, enforced by a
     * partial unique index on {@code user_id WHERE status = 'ACTIVE'}.
     *
     * <p>Note this deliberately does not see a PENDING_APPROVAL row. Someone waiting on an admin has no
     * workspace as far as the rest of the application is concerned, and no access to one.
     */
    Optional<WorkspaceMember> findByUserIdAndStatus(UUID userId, MemberStatus status);

    /**
     * The tenant-isolation check, and the reason it takes both ids.
     *
     * <p>Asking "is this user a member of <i>this</i> workspace?" in one query is what stops a caller
     * naming someone else's workspace id and being served their data. Nothing workspace-scoped should
     * load without this returning an active member first.
     */
    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndStatus(UUID workspaceId, UUID userId, MemberStatus status);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findByWorkspaceIdAndStatus(UUID workspaceId, MemberStatus status);

    /** Everything the user has going on — one active membership at most, plus any outstanding requests. */
    List<WorkspaceMember> findByUserId(UUID userId);

    /** Backs the last-admin guard: a workspace must never lose its only active ADMIN. */
    long countByWorkspaceIdAndRoleAndStatus(UUID workspaceId, WorkspaceRole role, MemberStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, MemberStatus status);

    Optional<WorkspaceMember> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
