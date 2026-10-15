package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidateScorecardResponse;
import app.lightmove.api.candidate.model.AiScorecardDraft;
import app.lightmove.api.candidate.service.CandidateScorecardService;
import app.lightmove.api.candidate.service.CandidateService;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Writes what a paid model call answered, asking once more when a racing edit wins the first write. Safe to repeat:
 * each write re-reads the row and fills only what nobody else holds, so the retry never overwrites the edit that won.
 */
@Component
@RequiredArgsConstructor
class AiDraftWriter {

    private final CandidateService candidates;
    private final CandidateScorecardService scorecards;

    CandidateResponse writeBackground(UUID projectId, UUID candidateId, UUID requestedBy,
                                      BackgroundProposals proposals) {
        return onceMoreIfRaced(() -> candidates.applyAiBackground(projectId, candidateId, requestedBy,
                proposals.background(), proposals.reading()));
    }

    CandidateScorecardResponse writeScorecard(UUID workspaceId, UUID projectId, UUID candidateId, UUID requestedBy,
                                              AiScorecardDraft draft) {
        return onceMoreIfRaced(() -> scorecards.applyAiDraft(workspaceId, projectId, candidateId, requestedBy, draft));
    }

    private static <T> T onceMoreIfRaced(Supplier<T> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException raced) {
            return write.get();
        }
    }
}
