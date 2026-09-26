package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * A company the mandate supplies itself, typed or captured by the plugin. The caller carries the
 * fields, so {@code source} {@code strategy} is refused. {@code status} defaults to in universe.
 */
public record CaptureCompanyRequest(
        @NotBlank(message = "A company name is required")
        @Size(max = 200, message = "A company name must be 200 characters or fewer")
        String companyName,

        @Size(max = 32)
        String source,

        @Size(max = 32)
        String status,

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

        @Size(max = 1000)
        String sourceUrl,

        @Size(max = 2000, message = "A note must be 2000 characters or fewer")
        String note,

        /** Keyed by {@code fieldKey}; {@code CustomColumnService.applyTo} decides what may be stored. */
        Map<String, String> customFields
) {}
