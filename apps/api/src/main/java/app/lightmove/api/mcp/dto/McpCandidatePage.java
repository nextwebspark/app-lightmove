package app.lightmove.api.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What {@code uncava_list_candidates} answers. */
public record McpCandidatePage(
        @Schema(description = "The executives of this page") List<McpCandidateRow> candidates,
        @Schema(description = "Executives matching across every page") long totalCount,
        @Schema(description = "Pass as cursor for the next page; absent on the last", nullable = true)
        @Nullable String nextCursor,
        @Schema(description = "Why this page is shorter than asked, and how to read on", nullable = true)
        @Nullable String notice
) {}
