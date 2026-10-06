package app.lightmove.api.mcp.dto;

import app.lightmove.api.publicapi.dto.PublicCandidate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An executive of a position as a tool lists them, with the whole public record when asked for detail. */
public record McpCandidateRow(
        @Schema(description = "This executive's id within the position") UUID id,
        @Schema(description = "The person's id, the same on every position that maps them") UUID personId,
        @Schema(description = "Their full name") String fullName,
        @Schema(description = "Their current title", nullable = true) @Nullable String title,
        @Schema(description = "The position's company they are mapped at, as uncava_list_companies names it",
                nullable = true)
        @Nullable UUID companyId,
        @Schema(description = "Their employer's name", nullable = true) @Nullable String companyName,
        @Schema(description = "Where they stand on this position: identified, contacted, engaged, interested, "
                + "notInterested, offLimits or outOfScope")
        String status,
        @Schema(description = "Every field of the executive; present only with response_format=detailed. Its contacts "
                + "and compensation are filled only where the connection holds their scopes", nullable = true)
        @Nullable PublicCandidate detail
) {

    public static McpCandidateRow of(PublicCandidate candidate, boolean detailed) {
        return new McpCandidateRow(candidate.id(), candidate.personId(), candidate.fullName(),
                McpFreeText.capped(candidate.title(), McpFreeText.MAX_LINE),
                candidate.companyId(), candidate.companyName(), candidate.status(),
                detailed ? McpFreeText.capped(candidate) : null);
    }
}
