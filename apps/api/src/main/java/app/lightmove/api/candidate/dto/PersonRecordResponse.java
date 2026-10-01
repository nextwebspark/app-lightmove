package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/** A workspace person outside any one mandate: who they are, how to reach them, and where they are mapped. */
public record PersonRecordResponse(
        UUID personId,
        String fullName,
        String title,
        String linkedinUrl,
        String locationCity,
        String locationCountry,
        CandidateContactsDto contacts,
        List<PersonPositionResponse> positions
) {}
