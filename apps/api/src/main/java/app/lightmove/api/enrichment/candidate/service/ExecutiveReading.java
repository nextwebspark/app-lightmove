package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.enrichment.candidate.model.ExecutiveReadingText;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The executive as a prompt reads them, for an AI run outside this feature (the Notetaker's interview plan and live
 * copilot): the same allowlisted profile and redacted material Assess with AI sends, never contacts or compensation.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveReading {

    private final CandidateService candidates;
    private final CandidateMaterialReader material;

    public ExecutiveReadingText of(UUID workspaceId, UUID projectId, UUID candidateId) {
        String profile = candidates.dossierOf(workspaceId, projectId, candidateId)
                .map(DossierText::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        MaterialText filed = material.read(projectId, candidateId);
        return new ExecutiveReadingText(profile, filed.notes(), filed.documents(), filed.noteCount(),
                filed.documentCount());
    }

    /** The profile alone, for a run that must stay small (the live copilot reads it every few seconds). */
    public String profileOf(UUID workspaceId, UUID projectId, UUID candidateId) {
        return candidates.dossierOf(workspaceId, projectId, candidateId)
                .map(DossierText::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
