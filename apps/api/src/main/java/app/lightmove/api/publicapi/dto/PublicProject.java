package app.lightmove.api.publicapi.dto;

import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import app.lightmove.api.project.dto.ProjectResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "Project", description = "A position: one search or mapping mandate")
public record PublicProject(
        @Schema(description = "The position's id") UUID id,
        @Schema(description = "The role being filled", example = "Chief Financial Officer") String title,
        @Schema(description = "The client or business unit's id") UUID clientId,
        @Schema(description = "The client or business unit hiring", example = "Group Finance") String clientName,
        @Schema(description = "MAPPING delivers a mapped universe; SEARCH runs through to a shortlist")
        ProjectType type,
        @Schema(description = "The furthest stage the position has reached") ProjectStage stage,
        @Schema(description = "When the work started", nullable = true, example = "2026-09-01") LocalDate startDate,
        @Schema(description = "When the map or shortlist is due", nullable = true, example = "2026-11-30")
        LocalDate deliveryDate,
        @Schema(description = "When the hire is wanted", nullable = true, example = "2027-01-15") LocalDate targetDate,
        @Schema(description = "Companies in the universe or shortlisted, declined ones left out", example = "48")
        long companies,
        @Schema(description = "Executives still in the running", example = "112") long candidates,
        @Schema(description = "When the position was created") Instant createdAt
) {

    public static PublicProject of(ProjectResponse project) {
        return new PublicProject(project.id(), project.positionTitle(), project.clientId(), project.clientName(),
                project.projectType(), project.stage(), project.startDate(), project.deliveryDate(),
                project.targetDate(), project.companies(), project.candidates(), project.createdAt());
    }
}
