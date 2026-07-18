package app.lightmove.api.workspace.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.workspace.constant.MemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A user's membership of one workspace, and the workspace roles they hold there.
 *
 * <p>This is the join that makes tenancy real: authorisation asks this table — not the user — what
 * someone may do, and always in the context of a specific workspace.
 *
 * <p>There is exactly one way in: {@link #invite} — the workspace's creator granting themselves ADMIN,
 * or an admin naming someone. An admin naming a person <i>is</i> the decision, so every membership is
 * {@link MemberStatus#ACTIVE} from its first moment. Nobody can ask their way in; membership is
 * invitation-only.
 *
 * <p>Roles are a set, not a single value — a member may hold several, and their permissions are the
 * union of the roles' actions (see {@code core/security/rbac}).
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

    /** WORKSPACE-scope catalog rows; the assignment table's composite FK refuses any other scope. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "app_lm_workspace_member_role",
            joinColumns = @JoinColumn(name = "member_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemberStatus status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    /** Who let them in. Compliance wants to know who named someone. */
    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The workspace's creator, or someone an admin invited. Active from the start. */
    public static WorkspaceMember invite(UUID workspaceId, UUID userId, Set<Role> roles, UUID invitedBy) {
        WorkspaceMember member = new WorkspaceMember();
        member.workspaceId = workspaceId;
        member.userId = userId;
        member.roles = new HashSet<>(roles);
        member.status = MemberStatus.ACTIVE;
        member.joinedAt = Instant.now();
        member.decidedBy = invitedBy;
        member.decidedAt = Instant.now();
        return member;
    }

    public boolean isActive() {
        return status.grantsAccess();
    }

    /** Replace-set semantics: the caller states the full set the member should hold afterwards. */
    public void changeRoles(Set<Role> newRoles) {
        if (!isActive()) {
            throw new IllegalStateException("Only an active membership can change roles, was " + status);
        }
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    /** Frees the one-active-membership index, so the person can join or create another workspace. */
    public void remove() {
        if (!isActive()) {
            throw new IllegalStateException("Only an active membership can be removed, was " + status);
        }
        this.status = MemberStatus.REMOVED;
    }
}
