package app.lightmove.api.mcp.dto;

import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import app.lightmove.api.publicapi.dto.PublicProject;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A position as a tool lists it: what to choose by, and the whole public record when asked for detail. */
public record McpPositionRow(
        @Schema(description = "The position's id, which every other tool takes as positionId") UUID id,
        @Schema(description = "The role being filled") String title,
        @Schema(description = "The client or business unit hiring") String clientName,
        @Schema(description = "MAPPING delivers a mapped universe; SEARCH runs through to a shortlist") ProjectType type,
        @Schema(description = "The furthest stage the position has reached") ProjectStage stage,
        @Schema(description = "Every field of the position; present only with response_format=detailed",
                nullable = true)
        @Nullable PublicProject detail
) {

    public static McpPositionRow of(PublicProject project, boolean detailed) {
        return new McpPositionRow(project.id(), project.title(), project.clientName(), project.type(), project.stage(),
                detailed ? project : null);
    }
}
