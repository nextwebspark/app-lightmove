package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * The company's own facts, replaced whole — what the Companies panel's Edit form submits for a
 * company the mandate supplied itself.
 *
 * <p>A PUT rather than more optional fields on {@link UpdateTriageCompanyRequest}: that one is a
 * triage change where a null leaves the other half be, while this is the whole form, so an omitted
 * field is a <i>cleared</i> field and a partial-merge endpoint could not express clearing a headcount.
 *
 * <p>{@code source}, {@code status} and {@code sourceUrl} are provenance and are not rewritable.
 * {@code note} stays on the PATCH, because it remains editable on the companies this endpoint refuses.
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

        // A headcount, not a population: the ceiling is a typo guard, and zero is a legitimate figure
        // for a holding company or a newly incorporated entity.
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

        /**
         * Values for this mandate's custom columns, keyed by each column's {@code fieldKey}.
         * CustomColumnService.applyTo states what a row may store.
         */
        Map<String, String> customFields
) {}
