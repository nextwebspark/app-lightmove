package app.lightmove.api.workspace.model;
import app.lightmove.api.workspace.constant.WorkspaceRole;
import app.lightmove.api.workspace.constant.MemberStatus;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A user's membership of one workspace, and the role they hold there.
 *
 * <p>This is the join that makes tenancy real: authorisation asks this table — not the user — what
 * someone may do, and always in the context of a specific workspace.
 *
 * <p>There are two ways in, and they differ in exactly one respect: whether a human decided.
 *
 * <ul>
 *   <li>{@link #invite} — an admin named this person. That <i>is</i> the decision, so they land
 *       {@link MemberStatus#ACTIVE} immediately.
 *   <li>{@link #requestToJoin} — this person found the workspace on their own email domain and asked.
 *       Nobody has decided anything yet, so they land {@link MemberStatus#PENDING_APPROVAL} with no
 *       access at all until an admin approves.
 * </ul>
 */
@Entity
@Table(name = "app_lm_workspace_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status;

    /** Null while pending — they have asked to join, they have not joined. */
    @Column(name = "joined_at")
    private Instant joinedAt;

    /** Who approved or rejected them. Compliance wants to know who let someone in. */
    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The workspace's creator, or someone an admin invited. Active from the start. */
    public static WorkspaceMember invite(UUID workspaceId, UUID userId, WorkspaceRole role, UUID invitedBy) {
        WorkspaceMember member = new WorkspaceMember();
        member.workspaceId = workspaceId;
        member.userId = userId;
        member.role = role;
        member.status = MemberStatus.ACTIVE;
        member.joinedAt = Instant.now();
        member.decidedBy = invitedBy;
        member.decidedAt = Instant.now();
        return member;
    }

    /**
     * Someone asking to join a workspace they found on their email domain.
     *
     * <p>The role is provisional — a request, not a grant. The approving admin sets the real one, which
     * is why nobody can walk in and declare themselves an ADMIN.
     */
    public static WorkspaceMember requestToJoin(UUID workspaceId, UUID userId, WorkspaceRole requestedRole) {
        WorkspaceMember member = new WorkspaceMember();
        member.workspaceId = workspaceId;
        member.userId = userId;
        member.role = requestedRole == null ? WorkspaceRole.RESEARCHER : requestedRole;
        member.status = MemberStatus.PENDING_APPROVAL;
        return member;
    }

    public boolean isActive() {
        return status.grantsAccess();
    }

    public boolean isPending() {
        return status == MemberStatus.PENDING_APPROVAL;
    }

    /**
     * An admin lets them in, and chooses the role themselves — the requested one was only ever a
     * suggestion.
     */
    public void approve(UUID adminUserId, WorkspaceRole grantedRole) {
        if (!isPending()) {
            throw new IllegalStateException("Only a pending membership can be approved, was " + status);
        }
        this.status = MemberStatus.ACTIVE;
        this.role = grantedRole == null ? this.role : grantedRole;
        this.joinedAt = Instant.now();
        this.decidedBy = adminUserId;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID adminUserId) {
        if (!isPending()) {
            throw new IllegalStateException("Only a pending membership can be rejected, was " + status);
        }
        this.status = MemberStatus.REJECTED;
        this.decidedBy = adminUserId;
        this.decidedAt = Instant.now();
    }

    public void changeRole(WorkspaceRole newRole) {
        if (!isActive()) {
            throw new IllegalStateException("Only an active membership can change role, was " + status);
        }
        this.role = newRole;
    }

    /** Frees the one-active-membership index, so the person can join or create another workspace. */
    public void remove() {
        if (!isActive()) {
            throw new IllegalStateException("Only an active membership can be removed, was " + status);
        }
        this.status = MemberStatus.REMOVED;
    }
}
