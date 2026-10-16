package app.uncava.api.enrichment.candidate.service;

import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.candidate.dto.CandidateScorecardResponse;
import app.uncava.api.candidate.model.AiScorecardDraft;
import app.uncava.api.candidate.model.CandidateScorecard;
import app.uncava.api.candidate.service.CandidateScorecardService;
import app.uncava.api.candidate.service.CandidateService;
import app.uncava.api.core.error.service.ConstraintViolations;
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

    /** Asks again only after a race — a lost {@code @Version}, or two first saves of one scorecard. */
    private static <T> T onceMoreIfRaced(Supplier<T> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException raced) {
            return write.get();
        } catch (DataIntegrityViolationException refused) {
            if (!CandidateScorecard.ONE_PER_MAPPING.equals(ConstraintViolations.nameOf(refused))) {
                throw refused;
            }
            return write.get();
        }
    }
}
