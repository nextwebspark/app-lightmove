package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One search mandate inside exactly one workspace. Starts at BRIEF; no screen sets a stage yet. */
@Entity
@Table(name = "app_lm_project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "position_title", nullable = false, length = 160)
    private String positionTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStage stage = ProjectStage.BRIEF;

    @Setter
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 16)
    private ProjectType projectType = ProjectType.SEARCH;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "mapping_target_date")
    private LocalDate mappingTargetDate;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** Edited on the Position screen's step one. */
    public void rename(String positionTitle) {
        this.positionTitle = positionTitle.trim();
    }

    /** When the client expects the work back: the delivery date, or the hire date on a mandate older than V73. */
    public LocalDate deadline() {
        return deliveryDate != null ? deliveryDate : targetDate;
    }

    public static Project create(UUID workspaceId, UUID clientId, String positionTitle,
                                 LocalDate targetDate, ProjectType projectType, ProjectTimeline timeline,
                                 UUID createdBy) {
        Project project = new Project();
        project.workspaceId = workspaceId;
        project.clientId = clientId;
        project.positionTitle = positionTitle.trim();
        project.targetDate = targetDate;
        project.projectType = projectType;
        project.startDate = timeline.startDate();
        project.deliveryDate = timeline.deliveryDate();
        project.mappingTargetDate = timeline.mappingTargetDate();
        project.createdBy = createdBy;
        return project;
    }
}
