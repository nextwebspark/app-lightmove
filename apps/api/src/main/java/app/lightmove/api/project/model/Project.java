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

/**
 * One search mandate, always inside exactly one workspace. Starts at BRIEF; no stage mutator exists
 * yet because no screen sets a stage — that arrives with the Project screen.
 *
 * <p>It carries two kinds of date and they are not interchangeable. The milestones say when the
 * mandate owes its client something, and health is measured against them. {@code targetDate} is the
 * brief's own target start — when the hire should begin — and drives nothing.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 32)
    private ProjectType projectType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "mapping_target_date")
    private LocalDate mappingTargetDate;

    @Column(name = "shortlist_target_date")
    private LocalDate shortlistTargetDate;

    @Setter
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** The mandate keeps one role title, and the Position screen's step one is where it is edited. */
    public void rename(String positionTitle) {
        this.positionTitle = positionTitle.trim();
    }

    /** The four fields move together, already defaulted and checked by {@link MandateTimeline#requested}. */
    public void retime(MandateTimeline timeline) {
        this.projectType = timeline.type();
        this.startDate = timeline.startDate();
        this.mappingTargetDate = timeline.mappingTarget();
        this.shortlistTargetDate = timeline.shortlistTarget();
    }

    public MandateTimeline timeline() {
        return new MandateTimeline(projectType, startDate, mappingTargetDate, shortlistTargetDate);
    }

    public static Project create(UUID workspaceId, UUID clientId, String positionTitle,
                                 MandateTimeline timeline, LocalDate targetDate, UUID createdBy) {
        Project project = new Project();
        project.workspaceId = workspaceId;
        project.clientId = clientId;
        project.positionTitle = positionTitle.trim();
        project.retime(timeline);
        project.targetDate = targetDate;
        project.createdBy = createdBy;
        return project;
    }
}
