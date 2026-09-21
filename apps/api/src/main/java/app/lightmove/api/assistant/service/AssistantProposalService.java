package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.dto.AcceptProposalRequest;
import app.lightmove.api.assistant.dto.AssistantProposalDto;
import app.lightmove.api.assistant.model.AssistantEvent;
import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposalOrigin;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.assistant.repository.AssistantEventRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.dto.AddSelectedTriageCompaniesRequest;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Filing what the assistant proposed — the half a person performs.
 *
 * <p><b>Every fact this authorises against is server-stored.</b> The request names a turn and some
 * refs, nothing else: the mandate comes out of the proposal event, which the proposing tool call was
 * already authorised against, and the company fields come from the same place. That is #425's rule
 * applied to the second half — a request's own claim about what it may touch is not evidence — and
 * it is why there is no {@code projectId} path variable to disagree with the stored one.
 *
 * <p>It opens no write path of its own. Both doors already exist and both are {@code WORK_EXECUTE}:
 * a universe row files through {@code addSelected} in one statement, and a row nobody resolved files
 * through {@code capture}, which is documented as exactly that mirror image.
 */
@Service
@RequiredArgsConstructor
public class AssistantProposalService {

    private static final List<String> PROPOSAL_KINDS = List.of(
            AssistantEventKind.PROPOSAL.wire(), AssistantEventKind.PROPOSAL_ACCEPTED.wire());

    private final AssistantTurnRepository turns;
    private final AssistantEventRepository events;
    private final AssistantEventAppender appender;
    private final ProjectAccess projectAccess;
    private final TriageCompanyService triage;
    private final AuditService audit;
    private final ObjectMapper json;

    /**
     * <b>Deliberately not {@code @Transactional}.</b> {@code addSelected} and {@code capture} each
     * open their own, and wrapping them here would hold one connection across the whole batch against
     * a pool of five — while buying nothing, because the appended event is a record of writes that
     * have already committed and must survive them.
     */
    public TriageBulkAddResponse accept(UUID turnId, UUID userId, UUID workspaceId,
                                        AcceptProposalRequest request, HttpServletRequest httpRequest) {
        AssistantProposal proposal = requireUnaccepted(turnId, userId, workspaceId);
        projectAccess.requireAction(userId, workspaceId, proposal.projectId(),
                ProjectAction.WORK_EXECUTE);

        List<ProposedCompany> chosen = proposal.refs(request.refs());
        if (chosen.size() != request.refs().stream().distinct().count()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Every company must be one this proposal offered");
        }

        TriageBulkAddResponse filed = file(userId, workspaceId, proposal.projectId(), chosen,
                request.status(), httpRequest);

        recordAccepted(turnId, request.status(), chosen, filed);

        audit.event(WorkspaceEventType.ASSISTANT_PROPOSAL_ACCEPTED)
                .actor(userId).workspace(workspaceId).target("project", proposal.projectId())
                .from(httpRequest)
                .detail("turnId", turnId)
                .detail("added", String.valueOf(filed.added()))
                .detail("skipped", String.valueOf(filed.skipped()))
                .record();
        return filed;
    }

    /** The proposal on a turn, with its outcome where it has one — what a thread read renders. */
    public Optional<AssistantProposalDto> of(UUID turnId) {
        return read(events.findByTurnIdAndKindInOrderBySeqAsc(turnId, PROPOSAL_KINDS));
    }

    /** The same across a thread's turns, in one query rather than one per turn. */
    public Map<UUID, AssistantProposalDto> byTurn(List<UUID> turnIds) {
        if (turnIds.isEmpty()) {
            return Map.of();
        }
        return events.findByTurnIdInAndKindInOrderBySeqAsc(turnIds, PROPOSAL_KINDS).stream()
                .collect(Collectors.groupingBy(AssistantEvent::getTurnId))
                .entrySet().stream()
                .flatMap(turn -> read(turn.getValue()).map(dto -> Map.entry(turn.getKey(), dto)).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Writes the outcome, and turns a lost race into the answer written for it.
     *
     * <p>{@code requireUnaccepted} is a read, so two accepts arriving together both pass it. The
     * rows survive that — {@code addSelected} ignores held companies and {@code capture} answers
     * {@code TRIAGE_COMPANY_ALREADY_HELD}, which counts as a skip — so nothing is filed twice and
     * exactly one of the two events lands. The loser collides on V65's unique index, and without
     * this it would surface as a 500 beside a proposal that had just been filed successfully.
     *
     * <p>Caught here rather than inside the appender, which is the distinction its own javadoc
     * draws: this method is outside that transaction, so the rollback has already happened and
     * there is nothing left to poison.
     */
    private void recordAccepted(UUID turnId, String status, List<ProposedCompany> chosen,
                                TriageBulkAddResponse filed) {
        try {
            appender.append(turnId, AssistantEventKind.PROPOSAL_ACCEPTED, Map.of(
                    "status", status == null ? "" : status,
                    "refs", chosen.stream().map(ProposedCompany::ref).toList(),
                    "added", filed.added(),
                    "skipped", filed.skipped()));
        } catch (DataIntegrityViolationException collided) {
            if (events.findByTurnIdAndKindInOrderBySeqAsc(turnId, PROPOSAL_KINDS).stream()
                    .anyMatch(AssistantProposalService::isAccepted)) {
                throw ApiException.of(ErrorCode.ASSISTANT_PROPOSAL_ALREADY_ACCEPTED);
            }
            throw collided;
        }
    }

    /**
     * Groups the chosen rows by the door each files through and sums one honest answer.
     *
     * <p>The universe rows go in one statement, which is what keeps a forty-company accept a single
     * write. The rest go one at a time because nothing resolved them, and a company the mandate
     * already holds is counted as skipped rather than failing the batch — refusing forty because one
     * was a duplicate would be the opposite of what the count is for.
     *
     * <p><b>These commits are not one transaction, and a failure part way through is visible.</b>
     * A {@code capture} that fails for any other reason propagates with the batch and the captures
     * before it already written, and no {@code PROPOSAL_ACCEPTED} event — so the card is still
     * acceptable, and a retry re-files harmlessly but reports those companies as <i>skipped</i>,
     * which is the dishonest count displaced rather than avoided. Latent while every row is
     * {@link ProposalOrigin#UNIVERSE}, since that path is one statement that either commits or does
     * not; it becomes reachable with the first tool that emits the other origins.
     */
    private TriageBulkAddResponse file(UUID userId, UUID workspaceId, UUID projectId,
                                       List<ProposedCompany> chosen, String status,
                                       HttpServletRequest httpRequest) {
        List<String> universeIds = chosen.stream()
                .filter(company -> company.origin() == ProposalOrigin.UNIVERSE)
                .map(ProposedCompany::apolloAccountId)
                .toList();

        TriageBulkAddResponse fromUniverse = universeIds.isEmpty()
                ? new TriageBulkAddResponse(0, 0)
                : triage.addSelected(userId, workspaceId, projectId,
                        new AddSelectedTriageCompaniesRequest(universeIds, status),
                        TriageCompanySource.ASSISTANT, httpRequest);

        int added = fromUniverse.added();
        int skipped = fromUniverse.skipped();
        for (ProposedCompany company : chosen) {
            if (company.origin() == ProposalOrigin.UNIVERSE) {
                continue;
            }
            if (captured(userId, workspaceId, projectId, company, status, httpRequest)) {
                added++;
            } else {
                skipped++;
            }
        }
        return new TriageBulkAddResponse(added, skipped);
    }

    /** False where the mandate already holds the company, which is a skip and not a failure. */
    private boolean captured(UUID userId, UUID workspaceId, UUID projectId, ProposedCompany company,
                             String status, HttpServletRequest httpRequest) {
        try {
            triage.capture(userId, workspaceId, projectId, asCapture(company, status), httpRequest);
            return true;
        } catch (ApiException held) {
            if (held.getCode() == ErrorCode.TRIAGE_COMPANY_ALREADY_HELD) {
                return false;
            }
            throw held;
        }
    }

    /**
     * A proposed row as the capture door takes it — the four fields the card showed and nothing
     * invented to fill the rest. Named rather than inlined because the door takes fifteen components
     * positionally, and eleven nulls in a row is a line nobody can check by reading it.
     */
    private static CaptureCompanyRequest asCapture(ProposedCompany company, String status) {
        String companyName = company.companyName();
        String source = TriageCompanySource.ASSISTANT.value();
        String country = company.country();
        Integer employees = company.employees();
        return new CaptureCompanyRequest(companyName, source, status, null, country, null, employees,
                null, null, null, null, null, null, null, null);
    }

    private AssistantProposal requireUnaccepted(UUID turnId, UUID userId, UUID workspaceId) {
        // The turn row carries workspace and actor, so this is the same ownership check the stream
        // makes — and someone else's turn is a 404, never a 403.
        AssistantTurn turn = turns.findByIdAndWorkspaceIdAndActorUserId(turnId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        // A running turn is still appending to this log from the worker, and the appender allocates
        // max(seq) + 1 on the assumption that only one thread is. The card is live well before the
        // answer finishes, so this is the ordinary case rather than a corner of one.
        if (turn.getStatus() == AssistantTurnStatus.RUNNING) {
            throw ApiException.of(ErrorCode.ASSISTANT_TURN_STILL_ANSWERING);
        }
        List<AssistantEvent> found = events.findByTurnIdAndKindInOrderBySeqAsc(turnId, PROPOSAL_KINDS);
        if (found.stream().anyMatch(AssistantProposalService::isAccepted)) {
            throw ApiException.of(ErrorCode.ASSISTANT_PROPOSAL_ALREADY_ACCEPTED);
        }
        return found.stream()
                .filter(AssistantProposalService::isProposal)
                .findFirst()
                .map(this::toProposal)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private Optional<AssistantProposalDto> read(List<AssistantEvent> found) {
        return found.stream()
                .filter(AssistantProposalService::isProposal)
                .findFirst()
                .map(event -> AssistantProposalDto.of(toProposal(event),
                        found.stream()
                                .filter(AssistantProposalService::isAccepted)
                                .findFirst()
                                .map(this::toAccepted)
                                .orElse(null)));
    }

    private AssistantProposal toProposal(AssistantEvent event) {
        return json.convertValue(event.getPayload(), AssistantProposal.class);
    }

    private AssistantProposalDto.Accepted toAccepted(AssistantEvent event) {
        return json.convertValue(event.getPayload(), AssistantProposalDto.Accepted.class);
    }

    private static boolean isProposal(AssistantEvent event) {
        return AssistantEventKind.PROPOSAL.wire().equals(event.getKind());
    }

    private static boolean isAccepted(AssistantEvent event) {
        return AssistantEventKind.PROPOSAL_ACCEPTED.wire().equals(event.getKind());
    }
}
