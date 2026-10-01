package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A workspace person outside any one mandate, as the Candidates drawer reads them: the profile every
 * position shares, how to reach them, where they are mapped, and the team's own facts about them —
 * owner, tags, do not contact. Staff-only; a client seat reaches none of it.
 */
public record PersonRecordResponse(
        UUID personId,
        String fullName,
        String title,
        String companyName,
        /** A {@code Seniority} wire token. */
        String seniority,
        String linkedinUrl,
        String locationCity,
        String locationCountry,
        String nationality,
        /** A {@code Gender} wire token. */
        String gender,
        Integer yearsExperience,
        String summary,
        CandidateCompensationDto compensation,
        List<CandidateCareerEntryDto> career,
        CandidateContactsDto contacts,
        List<PersonPositionResponse> positions,
        UUID ownerUserId,
        /** Null unless the person is marked do not contact. */
        DoNotContactResponse doNotContact,
        List<UUID> tagIds,
        /** A {@code CandidateSource} wire token: the door the person first came through. */
        String source,
        Instant addedAt,
        UUID addedByUserId,
        String addedByName
) {}
