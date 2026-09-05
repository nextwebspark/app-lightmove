package app.lightmove.api.candidate.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * An executive, as the Add and Edit drawer submits them. One record for both writes: the drawer holds
 * every field and sends every field either way, so a create and a full replace are the same payload
 * and there is no second shape to keep in step.
 *
 * <p>Only the name is required. Research arrives in pieces — a name, a company, a rough title from a
 * conference — and refusing the row until the package is established would send that name into a
 * spreadsheet.
 *
 * <p>{@code triageCompanyId} is the mandate's own company row, not an Apollo id, and it is optional:
 * an executive whose employer is not in the mandate's universe is still worth mapping. Where it is
 * given, the server snapshots that company's name and ignores {@code employerName} — the two must not
 * be able to disagree.
 */
public record SaveCandidateRequest(
        UUID triageCompanyId,

        @NotBlank(message = "A name is required")
        @Size(max = 200, message = "A name must be 200 characters or fewer")
        String fullName,

        @Size(max = 200)
        String title,

        /** A {@code Seniority} wire token — "Board", "N-1", … Omitted means not established. */
        @Size(max = 16)
        String seniority,

        /** A {@code CandidateStatus} wire token. Omitted means identified, where every profile starts. */
        @Size(max = 32)
        String status,

        /** Ignored when {@code triageCompanyId} names one of the mandate's companies. */
        @Size(max = 200)
        String employerName,

        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @Email(message = "That doesn't look like a valid email")
        @Size(max = 320)
        String email,

        @Size(max = 50)
        String phone,

        @Size(max = 500)
        String linkedinUrl,

        @Size(max = 100)
        String locationCountry,

        @Size(max = 100)
        String locationCity,

        @Size(max = 100)
        String nationality,

        @PositiveOrZero(message = "Years of experience cannot be negative")
        @Max(value = 70, message = "That figure looks like a typo")
        Integer yearsExperience,

        @Size(max = 4000)
        String summary,

        @Size(max = 2000)
        String note,

        @Valid
        CandidateCompensationDto compensation,

        @Valid
        @Size(max = 25, message = "A career history holds 25 posts at most")
        List<CandidateCareerEntryDto> career,

        @Size(max = 20, message = "20 languages is more than anyone speaks")
        List<@Size(max = 60) String> languages,

        /** A {@code CandidateSource} wire token. Omitted means typed in by hand. */
        @Size(max = 32)
        String source,

        /** Where the plugin read the profile. Ignored for a profile typed in by hand. */
        @Size(max = 1000)
        String sourceUrl,

        /**
         * Values for this mandate's custom columns, keyed by each column's {@code fieldKey}. Null
         * leaves every custom column alone — a client that does not render them (an older SPA, the
         * extension, a script) must be able to save a profile without wiping columns it never showed.
         * A key the mandate has not defined is dropped, and a blank value clears that one column.
         */
        Map<String, String> customFields
) {}
