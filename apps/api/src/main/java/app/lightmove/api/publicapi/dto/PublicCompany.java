package app.lightmove.api.publicapi.dto;

import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "Company", description = "A company a position has taken into its universe, as it was filed")
public record PublicCompany(
        @Schema(description = "This company's id within the position; an executive's companyId names it")
        UUID id,
        @Schema(description = "Where the position has filed it",
                allowableValues = {"inUniverse", "shortlisted", "declined"}, example = "shortlisted")
        String stage,
        @Schema(description = "The company's name", example = "ACWA Power") String name,
        @Schema(description = "Its industry", nullable = true, example = "utilities") String industry,
        @Schema(description = "Its headquarters country", nullable = true, example = "Saudi Arabia") String country,
        @Schema(description = "Its headquarters city", nullable = true, example = "Riyadh") String city,
        @Schema(description = "Headcount", nullable = true, example = "4200") Integer employees,
        @Schema(description = "Annual revenue in USD", nullable = true, example = "1600000000") Long annualRevenue,
        @Schema(description = "Its website", nullable = true, example = "https://www.acwapower.com") String website,
        @Schema(description = "Its LinkedIn page", nullable = true,
                example = "https://www.linkedin.com/company/acwa-power") String linkedinUrl,
        @Schema(description = "The year it was founded", nullable = true, example = "2004") Integer foundedYear,
        @Schema(description = "A short description", nullable = true) String description,
        @Schema(description = "Its logo", nullable = true) String logoUrl,
        @Schema(description = "The team searched it and found no executive to map", example = "false")
        boolean noExecutiveFound,
        @Schema(description = "When the position took it in") Instant addedAt
) {

    public static PublicCompany of(TriageCompanyResponse company) {
        return new PublicCompany(company.id(), company.status(), company.companyName(), company.industry(),
                company.companyCountry(), company.companyCity(), company.numEmployees(), company.annualRevenue(),
                company.website(), company.companyLinkedinUrl(), company.foundedYear(), company.shortDescription(),
                company.logoUrl(), company.noExecutiveFound(), company.addedAt());
    }
}
