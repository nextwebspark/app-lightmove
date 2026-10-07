package app.lightmove.api.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompanyDetail", description = "One company of a position, with the executives mapped at it")
public record PublicCompanyDetail(
        @Schema(description = "The company") PublicCompany company,
        @Schema(description = "Its executives, first mapped first, a page at a time. Null unless the key holds "
                + "candidates:read", nullable = true)
        PublicPage<PublicCandidate> executives
) {}
