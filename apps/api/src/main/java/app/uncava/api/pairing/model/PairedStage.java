package app.uncava.api.pairing.model;

import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.triagecompany.dto.TriageCompaniesResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One stage's companies and the executives the grid pairs with them. {@code people} keeps the order
 * they were mapped in, and is {@code byCompany} and {@code unassigned} together; {@code totalCandidates}
 * is the whole mandate's count, for a caller to hold against its cap.
 */
public record PairedStage(
        TriageCompaniesResponse companies,
        Map<UUID, List<CandidateResponse>> byCompany,
        List<CandidateResponse> unassigned,
        List<CandidateResponse> people,
        long totalCandidates
) {

    public List<CandidateResponse> peopleAt(UUID triageCompanyId) {
        return byCompany.getOrDefault(triageCompanyId, List.of());
    }
}
