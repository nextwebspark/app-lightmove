package app.lightmove.api.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What {@code uncava_search_positions} answers. */
public record McpPositionPage(
        @Schema(description = "The positions of this page") List<McpPositionRow> positions,
        @Schema(description = "Positions matching across every page") long totalCount,
        @Schema(description = "Pass as cursor for the next page; absent on the last", nullable = true)
        @Nullable String nextCursor,
        @Schema(description = "Why this page is shorter than asked, and how to read on", nullable = true)
        @Nullable String notice
) {}
