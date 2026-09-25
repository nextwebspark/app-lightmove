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
 * and sends every field either way, so a create and a full replace are the same payload.
 *
 * <p>Only the name is required — research arrives in pieces, and refusing the row until the package
 * is established would send that name into a spreadsheet instead.
 *
 * <p>{@code triageCompanyId} is the mandate's own company row, not an Apollo id, and it is optional.
 * Where it is given, the server snapshots that company's name and ignores {@code employerName} — the
 * two must not be able to disagree.
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

        /** One address, as the plugin's capture and a spreadsheet cell supply it. Joins the ledger. */
        @JsonDeserialize(converter = EmailAddressNormaliser.class)
        @Email(message = "That doesn't look like a valid email")
        @Size(max = 320)
        String email,

        /** One number, likewise. */
        @Size(max = 50)
        String phone,

        /** Every address, as the Add form supplies them — each with the kind the person gave it. */
        @Size(max = 10, message = "Ten email addresses is the most a profile holds")
        List<@Valid ContactEntryDto> emails,

        @Size(max = 10, message = "Ten phone numbers is the most a profile holds")
        List<@Valid ContactEntryDto> phones,

        @Size(max = 500)
        String linkedinUrl,

        @Size(max = 100)
        String locationCountry,

        @Size(max = 100)
        String locationCity,

        @Size(max = 100)
        String nationality,

        /** A {@code Gender} wire token. Omitted means nobody recorded it, which is not "other". */
        @Size(max = 16)
        String gender,

        @PositiveOrZero(message = "Years of experience cannot be negative")
        @Max(value = 70, message = "That figure looks like a typo")
        Integer yearsExperience,

        @Size(max = 4000)
        String summary,

        @Size(max = 2000)
        String note,

        @Valid
        CandidateCompensationDto compensation,

        @Size(max = 25, message = "A career history holds 25 posts at most")
        List<@Valid CandidateCareerEntryDto> career,

        @Size(max = 20, message = "20 languages is more than anyone speaks")
        List<@Size(max = 60) String> languages,

        /** A {@code CandidateSource} wire token. Omitted means typed in by hand. */
        @Size(max = 32)
        String source,

        /** Where the plugin read the profile. Ignored for a profile typed in by hand. */
        @Size(max = 1000)
        String sourceUrl,

        /**
         * Values for this mandate's custom columns, keyed by each column's {@code fieldKey}.
         * CustomColumnService.applyTo states what a row may store.
         */
        Map<String, String> customFields,

        /** True from the drawer's Background save: the reader has reviewed its AI-proposed values. */
        Boolean confirmBackground
) {}
