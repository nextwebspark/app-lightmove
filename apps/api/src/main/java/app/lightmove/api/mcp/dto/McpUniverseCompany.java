package app.lightmove.api.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A company of the stage with the executives mapped at it. */
public record McpUniverseCompany(
        @Schema(description = "The company") McpCompanyRow company,
        @Schema(description = "Its executives, first mapped first; empty where none is mapped")
        List<McpCandidateRow> executives
) {}
