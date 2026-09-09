package app.lightmove.api.talentmap.dto;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One stage of a mandate as the map draws it.
 *
 * <p>{@code locations} is keyed by row id — a company's or a candidate's — and an id absent from it is
 * a row with no point: no city and no country, or a place the vendor could not put anywhere. An
 * executive with no location of their own is absent too; the screen seats them at their company,
 * and this response states only what was actually resolved.
 *
 * <p>{@code geocodingPending} is how many places this read could not yet ask for. Above zero the
 * screen reads again shortly; a first read of a big import fills in over a few of them.
 */
public record TalentMapResponse(
        List<TriageCompanyResponse> companies,
        long totalCompanies,
        List<CandidateResponse> candidates,
        long totalCandidates,
        Map<UUID, MapLocationDto> locations,
        int geocodingPending
) {}
