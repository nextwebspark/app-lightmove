package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.dto.CandidateResponse;
import java.util.UUID;

/**
 * One mapped executive as the model may read them — an allowlist, like {@code CandidateDossier}:
 * contacts, compensation and custom fields never reach a prompt, and nationality and gender stay out
 * of anything the model ranks people with.
 */
public record MappedExecutiveSummary(UUID candidateId, String fullName, String title, String companyName,
                                     String seniority, String status, String locationCity,
                                     String locationCountry, Integer yearsExperience) {

    static MappedExecutiveSummary of(CandidateResponse candidate) {
        return new MappedExecutiveSummary(candidate.id(), candidate.fullName(), candidate.title(),
                candidate.companyName(), candidate.seniority(), candidate.status(), candidate.locationCity(),
                candidate.locationCountry(), candidate.yearsExperience());
    }
}
