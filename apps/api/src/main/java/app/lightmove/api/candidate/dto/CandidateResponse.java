package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One executive mapped for a mandate, as the Companies grid and the profile drawer read them.
 *
 * <p>{@code companyName} is carried rather than joined: it is the employer snapshotted when the row
 * was written, so it renders identically whether the person is still mapped to one of the mandate's
 * companies or that company has since been removed from it.
 */
public record CandidateResponse(
        UUID id,
        UUID triageCompanyId,
        String companyName,
        String fullName,
        String title,
        String seniority,
        String status,
        String linkedinUrl,
        String locationCountry,
        String locationCity,
        String nationality,
        /** A {@code Gender} wire token, or null where nobody recorded or confirmed one. */
        String gender,
        Integer yearsExperience,
        /**
         * Which of nationality/gender/yearsExperience currently hold a value an AI inference
         * proposed, not yet reviewed by a researcher's own edit (issue #458).
         */
        Set<String> aiInferredFields,
        String summary,
        String note,
        CandidateCompensationDto compensation,
        List<CandidateCareerEntryDto> career,
        List<String> languages,
        /** Filled by enrichment only; empty until research has run, and never edited by a screen. */
        List<CandidateEducationEntryDto> education,
        List<String> skills,
        String source,
        String sourceUrl,
        Map<String, String> customFields,
        Instant addedAt,
        /** When enrichment last filled this profile in; null while research is pending or off. */
        String enrichedAt,
        /** Every email and phone the mandate knows for them, and when each channel was last looked up. */
        CandidateContactsDto contacts
) {}
