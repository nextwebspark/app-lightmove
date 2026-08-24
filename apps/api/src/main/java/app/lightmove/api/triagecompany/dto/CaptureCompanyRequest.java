package app.lightmove.api.triagecompany.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A company captured from a web page, on its way into a mandate's triage.
 *
 * <p>Wider than {@link AddTriageCompanyRequest} on purpose, and the difference is the whole point of
 * the two existing separately. That one names an Apollo id and nothing else, because the universe can
 * be asked what the company is. This one describes a company the universe may never have heard of, so
 * the snapshot has to travel.
 *
 * <p>{@code apolloAccountId} is still preferred and still wins: when it is present the snapshot fields
 * below are <b>ignored</b> and resolved from the universe instead, so the rule that a client cannot
 * file a known company under a name of its own choosing survives intact. The fields are read only for
 * a company Apollo does not publish, where there is no other source for them.
 *
 * <p>{@code status} takes the {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus}
 * wire tokens, and the service refuses any but the two a capture may land in.
 */
public record CaptureCompanyRequest(

        @NotBlank(message = "A destination is required")
        @Size(max = 16)
        String status,

        @Size(max = 64)
        String apolloAccountId,

        @NotBlank(message = "A company name is required")
        @Size(max = 200)
        String companyName,

        @Size(max = 500)
        String website,

        @Size(max = 500)
        String linkedinUrl,

        @Size(max = 200)
        String industry,

        @Size(max = 120)
        String companyCountry,

        @Size(max = 120)
        String companyCity,

        @PositiveOrZero(message = "Headcount cannot be negative")
        Integer numEmployees,

        @PositiveOrZero(message = "Revenue cannot be negative")
        Long annualRevenue,

        @Size(max = 20, message = "A company can carry at most 20 tags")
        List<@NotBlank @Size(max = 40) String> tags,

        @Size(max = 2000)
        String note,

        @Size(max = 1000)
        String sourceUrl
) {}
