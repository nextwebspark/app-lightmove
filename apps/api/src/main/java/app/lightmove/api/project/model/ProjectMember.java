package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.ProjectRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A workspace member's seat on one project. References the membership row, not the user, so a team
 * physically cannot contain a member of another workspace. A partial unique index keeps exactly one
 * LEAD per project.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectRole role = ProjectRole.MEMBER;

    @Column(name = "added_by")
    private UUID addedBy;

    public static ProjectMember of(UUID projectId, UUID memberId, ProjectRole role, UUID addedBy) {
        ProjectMember seat = new ProjectMember();
        seat.projectId = projectId;
        seat.memberId = memberId;
        seat.role = role;
        seat.addedBy = addedBy;
        return seat;
    }

    public boolean isLead() {
        return role == ProjectRole.LEAD;
    }

    public void promote() {
        this.role = ProjectRole.LEAD;
    }

    public void demote() {
        this.role = ProjectRole.MEMBER;
    }
}
