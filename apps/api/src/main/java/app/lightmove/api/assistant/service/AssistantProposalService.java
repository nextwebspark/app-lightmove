package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.dto.AcceptProposalRequest;
import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantThread;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.ProposalOutcome;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.assistant.repository.AssistantThreadRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.dto.AddSelectedTriageCompaniesRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Files the ticked companies of a card: universe companies through the door Strategy's bulk add uses,
 * researched ones through the capture door with what their page said. The project and the companies
 * come from the stored turn, so the request can only choose among what was offered.
 */
@Service
@RequiredArgsConstructor
public class AssistantProposalService {

    private final AssistantTurnRepository turns;
    private final AssistantThreadRepository threads;
    private final ProjectAccess projectAccess;
    private final TriageCompanyService triage;

    public TriageBulkAddResponse accept(UUID turnId, UUID userId, UUID workspaceId,
                                        AcceptProposalRequest request, HttpServletRequest httpRequest) {
        AssistantTurn turn = turns.findByIdAndWorkspaceIdAndActorUserId(turnId, workspaceId, userId)
                .filter(found -> found.getProposal() != null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (turn.getProposalAccepted() != null) {
            throw ApiException.of(ErrorCode.ASSISTANT_PROPOSAL_ALREADY_ACCEPTED);
        }
        UUID projectId = threads.findById(turn.getThreadId())
                .map(AssistantThread::getProjectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        projectAccess.requireAction(userId, workspaceId, projectId, ProjectAction.WORK_EXECUTE);

        List<String> chosen = request.companyIds().stream().distinct().toList();
        AssistantProposal card = turn.getProposal();
        Set<String> offered = card.companies().stream()
                .map(ProposedCompany::key)
                .collect(Collectors.toSet());
        if (!offered.containsAll(chosen)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Every company must be one this proposal offered");
        }

        List<String> accountIds = chosen.stream().filter(key -> !card.researched().containsKey(key)).toList();
        TriageBulkAddResponse fromUniverse = accountIds.isEmpty() ? new TriageBulkAddResponse(0, 0)
                : triage.addSelected(userId, workspaceId, projectId,
                        new AddSelectedTriageCompaniesRequest(accountIds, request.status()),
                        TriageCompanySource.ASSISTANT, httpRequest);
        long researchedAdded = chosen.stream()
                .filter(card.researched()::containsKey)
                .filter(slug -> triage.captureResearched(userId, workspaceId, projectId,
                        card.researched().get(slug), TriageCompanySource.ASSISTANT, request.status(), httpRequest))
                .count();
        int researchedChosen = chosen.size() - accountIds.size();
        TriageBulkAddResponse filed = new TriageBulkAddResponse(
                fromUniverse.added() + (int) researchedAdded,
                fromUniverse.skipped() + researchedChosen - (int) researchedAdded);
        turn.recordAccepted(new ProposalOutcome(request.status(), filed.added(), filed.skipped()));
        turns.save(turn);
        return filed;
    }
}
