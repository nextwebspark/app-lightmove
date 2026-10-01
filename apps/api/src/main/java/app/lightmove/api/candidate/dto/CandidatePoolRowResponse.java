package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/** One person on the Candidates page: who, where, on which positions, how labelled, and the latest line. */
public record CandidatePoolRowResponse(
        UUID personId,
        String fullName,
        String title,
        /** The employer their most recent position recorded, else their current post. */
        String companyName,
        String locationCity,
        String locationCountry,
        String linkedinUrl,
        boolean doNotContact,
        /** Every position mapping them, most recently added first. */
        List<PersonPositionResponse> positions,
        List<UUID> tagIds,
        UUID ownerUserId,
        /** Their latest timeline line; null for someone nothing has been recorded about. */
        PersonTimelineEntryResponse lastActivity
) {}
