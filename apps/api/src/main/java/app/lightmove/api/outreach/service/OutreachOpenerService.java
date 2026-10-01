package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
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
    private final LlmBudgetGuard llmBudget;
    private final AuditService audit;

    public List<DraftedOpener> draft(UUID userId, UUID workspaceId, UUID projectId, List<UUID> candidateIds,
                                     HttpServletRequest httpRequest) {
        Map<UUID, CandidateDossier> dossiers = new LinkedHashMap<>();
        for (UUID candidateId : candidateIds.stream().distinct().toList()) {
            dossiers.put(candidateId, people.dossierOf(workspaceId, projectId, candidateId));
        }
        llmBudget.require(LlmBudget.OUTREACH_DRAFT, userId);
        OpenerBrief brief = briefOf(workspaceId, projectId);
        audit.projectEvent(ProjectEventType.OUTREACH_OPENERS_DRAFTED, userId, workspaceId, projectId, httpRequest)
                .detail("count", dossiers.size())
                .record();
        return draftAll(dossiers, brief);
    }

    /** Only the industry is read from the hiring company: its name never leaves this method. */
    OpenerBrief briefOf(UUID workspaceId, UUID projectId) {
        PositionDetailsDto details = positions.briefOf(workspaceId, projectId).details();
        String sector = clients.hiringProfileOfProject(workspaceId, projectId).industry();
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
