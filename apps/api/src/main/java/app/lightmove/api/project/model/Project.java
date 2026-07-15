package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.ProjectStage;
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

/**
 * One search mandate, always inside exactly one workspace. Starts at BRIEF; no stage mutator exists
 * yet because no screen sets a stage — that arrives with the Project screen.
 */
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

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public static Project create(UUID workspaceId, UUID clientId, String positionTitle,
                                 LocalDate targetDate, UUID createdBy) {
        Project project = new Project();
        project.workspaceId = workspaceId;
        project.clientId = clientId;
        project.positionTitle = positionTitle.trim();
        project.targetDate = targetDate;
        project.createdBy = createdBy;
        return project;
    }
}
