package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One executive mapped for a mandate, as the Companies grid and the profile drawer read them. The
 * profile is the workspace person's, shared with every other mandate mapping them; {@code status}
 * (a key of the workspace's pipeline catalog), {@code source} and {@code customFields} are this mandate's
 * own. A client seat reads this record, which is
 * why notes, the AI assessment and who filed the row are never on it.
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
        /** Which of nationality/gender/yearsExperience hold a model's proposal no researcher has changed. */
        Set<String> aiInferredFields,
        String summary,
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
        /** Every email and phone known for them on any mandate, and when each channel was last looked up. */
        CandidateContactsDto contacts,
        /** The workspace's person this row maps — one id across every mandate that holds them (V91). */
        UUID personId,
        /** The plugin read this person off that page, so the URL cannot be retyped (CANDIDATE_PROFILE_URL_LOCKED). */
        boolean linkedinUrlLocked,
        /** {@code status}'s label and colour in the workspace's catalog, drawn as held even once it is retired. */
        String statusLabel,
        String statusColour,
        /** Whether {@code status} is a way out of the process: Inactive on the board, approached no more. */
        boolean statusOutOfProcess,
        /** The stage {@code status} sits under — read through the status, never stored beside it. */
        CandidatePipelineStageDto pipelineStage,
        Instant statusChangedAt,
        /** Whether a client seat on the position sees this executive at all; staff always do. */
        boolean clientVisible,
        /** The Companies stage they are listed under — a {@code TriageCompanyStatus} wire token, their own (V150). */
        String triageStatus
) {}
