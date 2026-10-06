package app.lightmove.api.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What {@code uncava_list_companies} answers. */
public record McpCompanyPage(
        @Schema(description = "The companies of this page") List<McpCompanyRow> companies,
        @Schema(description = "Companies at this stage across every page") long totalCount,
        @Schema(description = "Pass as cursor for the next page; absent on the last", nullable = true)
        @Nullable String nextCursor,
        @Schema(description = "Why this page is shorter than asked, and how to read on", nullable = true)
        @Nullable String notice
) {}
