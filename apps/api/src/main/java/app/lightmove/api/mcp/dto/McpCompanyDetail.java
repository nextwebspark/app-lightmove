package app.lightmove.api.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What {@code uncava_get_company} answers: the company, and a page of the executives mapped at it. */
public record McpCompanyDetail(
        @Schema(description = "The company") McpCompanyRow company,
        @Schema(description = "Its executives, first mapped first; absent unless the connection holds candidates:read",
                nullable = true)
        @Nullable List<McpCandidateRow> executives,
        @Schema(description = "Its executives across every page; absent unless the connection holds candidates:read",
                nullable = true)
        @Nullable Long totalExecutives,
        @Schema(description = "Pass as cursor for the next page of executives; absent on the last", nullable = true)
        @Nullable String nextCursor,
        @Schema(description = "Why this page is shorter than asked, and how to read on", nullable = true)
        @Nullable String notice
) {}
