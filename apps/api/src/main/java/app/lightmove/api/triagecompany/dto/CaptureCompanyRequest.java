package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * A company the mandate supplies itself — typed in on the Companies screen, or read off a live page by
 * the browser plugin.
 *
 * <p>The mirror image of {@link AddTriageCompanyRequest}, which names a universe id and lets the server
 * resolve everything else. Here there is no universe row to resolve against, so the caller carries the
 * fields; the trade is that {@code source} must say so, and {@code strategy} is refused — a company
 * claiming to come from the market must come through the endpoint that reads the market.
 *
 * <p>{@code status} is the landing stage. It exists for the plugin's two destination buttons ("Add to
 * universe" / "Add to shortlist" in {@code Extension.dc.html}), which are one capture with two
 * different answers to where it lands. Omitted, it lands in universe like everything else.
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

        /** Where the plugin captured this from. Ignored for a company typed in by hand. */
        @Size(max = 1000)
        String sourceUrl,

        @Size(max = 2000, message = "A note must be 2000 characters or fewer")
        String note,

        /**
         * Values for this mandate's custom columns, keyed by each column's {@code fieldKey}. Null
         * leaves every custom column alone — a client that does not render them (an older SPA, the
         * extension, a script) must be able to save a row without wiping columns it never showed.
         * A key the mandate has not defined is dropped, and a blank value clears that one column.
         */
        Map<String, String> customFields
) {}
