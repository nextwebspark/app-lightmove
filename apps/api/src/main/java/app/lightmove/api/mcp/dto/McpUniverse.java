package app.lightmove.api.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What {@code uncava_get_universe} answers: one stage, its companies with their executives nested. */
public record McpUniverse(
        @Schema(description = "The stage read: inUniverse, shortlisted or declined") String stage,
        @Schema(description = "The stage's companies, by name") List<McpUniverseCompany> companies,
        @Schema(description = "Every company of the stage, including any this answer was cut short of")
        int totalCompanies,
        @Schema(description = "Executives at no company of the position; filled on inUniverse only")
        List<McpCandidateRow> unassigned,
        @Schema(description = "Why companies were left out, and how to read them", nullable = true)
        @Nullable String notice
) {}
