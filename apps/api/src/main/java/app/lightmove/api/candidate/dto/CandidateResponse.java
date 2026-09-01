package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        String email,
        String phone,
        String linkedinUrl,
        String locationCountry,
        String locationCity,
        String nationality,
        Integer yearsExperience,
        String summary,
        String note,
        CandidateCompensationDto compensation,
        List<CandidateCareerEntryDto> career,
        List<String> languages,
        String source,
        String sourceUrl,
        Map<String, String> customFields,
        Instant addedAt
) {}
