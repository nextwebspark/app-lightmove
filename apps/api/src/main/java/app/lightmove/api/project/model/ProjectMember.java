package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.security.rbac.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A member's seat on one project, referencing the membership row so a team cannot hold another
 * workspace's member. A role set, but {@code ProjectTeamService} allows one staff role plus CLIENT.
 */
@Entity
@Table(name = "app_lm_project_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    /** PROJECT-scope catalog rows; the assignment table's composite FK refuses any other scope. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "app_lm_project_member_role",
            joinColumns = @JoinColumn(name = "project_member_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Column(name = "added_by")
    private UUID addedBy;

    public static ProjectMember of(UUID projectId, UUID memberId, Set<Role> roles, UUID addedBy) {
        ProjectMember seat = new ProjectMember();
        seat.projectId = projectId;
        seat.memberId = memberId;
        seat.roles = new HashSet<>(roles);
        seat.addedBy = addedBy;
        return seat;
    }

    /** Replace-set semantics: the caller states the full set the seat should hold afterwards. */
    public void changeRoles(Set<Role> newRoles) {
        this.roles.clear();
        this.roles.addAll(newRoles);
    }
}
