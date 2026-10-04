package app.lightmove.api.talentmap.dto;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One stage as the map draws it. {@code locations} is keyed by row id; an absent id has no resolved
 * point. Above-zero {@code geocodingPending} means the screen should read again shortly.
 */
public record TalentMapResponse(
        List<TriageCompanyResponse> companies,
        long totalCompanies,
        List<CandidateResponse> candidates,
        long totalCandidates,
        Map<UUID, MapLocationDto> locations,
        int geocodingPending
) {}
