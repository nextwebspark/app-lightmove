package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * A mandate-supplied company's facts, replaced whole: an omitted field is a cleared one. Provenance is
 * not rewritable, and {@code note} stays on the PATCH, which also reaches market companies.
 */
public record EditTriageCompanyRequest(
        @NotBlank(message = "A company name is required")
        @Size(max = 200, message = "A company name must be 200 characters or fewer")
        String companyName,

        @Size(max = 200)
        String industry,

        @Size(max = 100)
        String companyCountry,

        @Size(max = 100)
        String companyCity,

        // The ceiling is a typo guard; zero is legitimate for a holding company.
        @PositiveOrZero(message = "Employees cannot be negative")
        @Max(value = 10_000_000, message = "That headcount looks like a typo")
        Integer numEmployees,

        @PositiveOrZero(message = "Revenue cannot be negative")
        Long annualRevenue,

        @Min(value = 1800, message = "That founding year looks like a typo")
        @Max(value = 2100, message = "That founding year looks like a typo")
        Integer foundedYear,

        @Size(max = 500)
        String website,

        @Size(max = 500)
        String companyLinkedinUrl,

        @Size(max = 2000)
        String shortDescription,

        /** Keyed by {@code fieldKey}; {@code CustomColumnService.applyTo} decides what may be stored. */
        Map<String, String> customFields
) {}
