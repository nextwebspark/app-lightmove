package app.uncava.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(name = "ProjectSummary", description = "Where a position stands: its companies by stage, its executives by "
        + "status and by pipeline stage, and its dates, in one read")
public record PublicPositionSummary(
        @Schema(description = "The position") PublicProject position,
        @Schema(description = "Companies at each stage") PublicStageCounts companies,
        @Schema(description = "Executives at each status key of the workspace's pipeline, in pipeline order, every "
                + "status present — the built-in identified, contacted, engaged, interested, notInterested, offLimits "
                + "and outOfScope always among them",
                example = "{\"identified\": 64, \"contacted\": 30, \"engaged\": 9, \"interested\": 4, "
                        + "\"notInterested\": 3, \"offLimits\": 1, \"outOfScope\": 2}")
        Map<String, Long> executives,
        @Schema(description = "Executives at each stage key of the workspace's pipeline, in pipeline order, every "
                + "stage present",
                example = "{\"identified\": 64, \"contacted\": 39, \"interested\": 4, \"qualified\": 0, "
                        + "\"shortlisted\": 0, \"declined\": 6}")
        Map<String, Long> pipelineStages,
        @Schema(description = "Universe or shortlisted companies with at least one executive mapped", example = "31")
        long mappedCompanies,
        @Schema(description = "The position's dates") PublicTimeline timeline
) {}
