package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.outreach.model.DraftedOpener;
import app.lightmove.api.outreach.model.OpenerBrief;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.service.ClientService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The review's openers. One press — a batch on entering Review, or one Redraft — is one unit of
 * {@link LlmBudget#OUTREACH_DRAFT}, whatever it holds, so reviewing ten people costs what one does.
 * The model is called outside any transaction, each person on its own thread.
 */
@Service
@RequiredArgsConstructor
public class OutreachOpenerService {

    private final CandidateOutreachService people;
    private final PositionService positions;
    private final ClientService clients;
    private final OutreachOpenerDrafter drafter;
    private final OutreachEligibility eligibility;
    private final LlmBudgetGuard llmBudget;
    private final AuditService audit;

    public List<DraftedOpener> draft(UUID userId, UUID workspaceId, UUID projectId, List<UUID> candidateIds,
                                     HttpServletRequest httpRequest) {
        List<UUID> distinct = candidateIds.stream().distinct().toList();
        requireAllMayBeApproached(workspaceId, projectId, distinct);
        Map<UUID, CandidateDossier> dossiers = new LinkedHashMap<>();
        for (UUID candidateId : distinct) {
            dossiers.put(candidateId, people.dossierOf(workspaceId, projectId, candidateId));
        }
        llmBudget.require(LlmBudget.OUTREACH_DRAFT, userId);
        OpenerBrief brief = briefOf(workspaceId, projectId);
        audit.projectEvent(ProjectEventType.OUTREACH_OPENERS_DRAFTED, userId, workspaceId, projectId, httpRequest)
                .detail("count", dossiers.size())
                .record();
        return draftAll(dossiers, brief);
    }

    /**
     * Decided before anything is spent or anyone's profile reaches the model: an opener is drafted only to
     * approach someone, so a person who may not be approached gets none.
     */
    private void requireAllMayBeApproached(UUID workspaceId, UUID projectId, List<UUID> candidateIds) {
        List<OutreachRecipient> recipients = people.recipientsOf(workspaceId, projectId, candidateIds, List.of());
        if (recipients.size() != candidateIds.size()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        if (eligibility.of(projectId, recipients).anySkipped(recipients)) {
            throw ApiException.of(ErrorCode.OUTREACH_PERSON_SKIPPED);
        }
    }

    private OpenerBrief briefOf(UUID workspaceId, UUID projectId) {
        PositionDetailsDto details = positions.briefOf(workspaceId, projectId).details();
        String sector = clients.industryOfProjectClient(workspaceId, projectId);
        return new OpenerBrief(details.roleTitle(), details.seniority() == null ? null : details.seniority().value(),
                sector, details.locationCity(), details.locationCountry());
    }

    private List<DraftedOpener> draftAll(Map<UUID, CandidateDossier> dossiers, OpenerBrief brief) {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<UUID, Future<String>> pending = new LinkedHashMap<>();
            dossiers.forEach((candidateId, dossier) ->
                    pending.put(candidateId, threads.submit(() -> drafter.draft(dossier, brief).orElse(null))));
            return pending.entrySet().stream()
                    .map(entry -> new DraftedOpener(entry.getKey(), resultOf(entry.getValue())))
                    .toList();
        }
    }

    private static String resultOf(Future<String> drafted) {
        try {
            return drafted.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception failed) {
            return null;
        }
    }
}
