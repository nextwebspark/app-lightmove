package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(name = "ProjectSummary", description = "Where a position stands: its companies by stage, its executives by "
        + "status, and its dates, in one read")
public record PublicPositionSummary(
        @Schema(description = "The position") PublicProject position,
        @Schema(description = "Companies at each stage") PublicStageCounts companies,
        @Schema(description = "Executives at each status, every status present: identified, contacted, engaged, "
                + "interested, notInterested, offLimits, outOfScope",
                example = "{\"identified\": 64, \"contacted\": 30, \"engaged\": 9, \"interested\": 4, "
                        + "\"notInterested\": 3, \"offLimits\": 1, \"outOfScope\": 2}")
        Map<String, Long> executives,
        @Schema(description = "Universe or shortlisted companies with at least one executive mapped", example = "31")
        long mappedCompanies,
        @Schema(description = "The position's dates") PublicTimeline timeline
) {}
