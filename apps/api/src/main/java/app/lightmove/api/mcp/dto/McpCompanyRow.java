package app.lightmove.api.mcp.dto;

import app.lightmove.api.publicapi.dto.PublicCompany;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A company of a position as a tool lists it, with the whole public record when asked for detail. */
public record McpCompanyRow(
        @Schema(description = "This company's id within the position; uncava_list_candidates takes it as companyId")
        UUID id,
        @Schema(description = "The company's name") String name,
        @Schema(description = "Where the position has filed it: inUniverse, shortlisted or declined") String stage,
        @Schema(description = "Its industry", nullable = true) @Nullable String industry,
        @Schema(description = "Its headquarters country", nullable = true) @Nullable String country,
        @Schema(description = "Every field of the company; present only with response_format=detailed",
                nullable = true)
        @Nullable PublicCompany detail
) {

    public static McpCompanyRow of(PublicCompany company, boolean detailed) {
        return new McpCompanyRow(company.id(), company.name(), company.stage(), company.industry(), company.country(),
                detailed ? McpFreeText.capped(company) : null);
    }
}
