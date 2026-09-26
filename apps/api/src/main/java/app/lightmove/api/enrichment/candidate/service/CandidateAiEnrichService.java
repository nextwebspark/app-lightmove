package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The drawer's AI deep enrich button: checks the candidate is this workspace's, spends the budget up
 * front so an exhausted one is refused to the person who pressed it, and queues the run.
 *
 * <p>{@code @Transactional} because the worker listens {@code AFTER_COMMIT}: published with no
 * transaction bound, the event would never be delivered.
 */
@Service
@RequiredArgsConstructor
public class CandidateAiEnrichService {

    private final CandidateService candidates;
    private final LlmBudgetGuard llmBudget;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    @Transactional
    public void request(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                        HttpServletRequest httpRequest) {
        candidates.requireCandidate(workspaceId, projectId, candidateId);
        llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, userId);
        audit.projectEvent(ProjectEventType.CANDIDATE_AI_ENRICH_REQUESTED, userId, workspaceId, projectId, httpRequest)
                .detail("candidateId", candidateId.toString())
                .record();
        events.publishEvent(new CandidateAiEnrichRequested(candidateId, projectId, workspaceId, userId,
                AiEnrichTrigger.BUTTON));
    }
}
