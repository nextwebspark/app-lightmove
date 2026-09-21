package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.dto.AcceptProposalRequest;
import app.lightmove.api.assistant.model.AssistantEvent;
import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.model.ProposalOrigin;
import app.lightmove.api.assistant.model.ProposedCompany;
import app.lightmove.api.assistant.repository.AssistantEventRepository;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.dto.AddSelectedTriageCompaniesRequest;
import app.lightmove.api.triagecompany.dto.TriageBulkAddResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * That each proposed row files through the door its origin names, and that the answer adds up.
 *
 * <p>Drives the service with a hand-built proposal event rather than waiting for a tool that emits
 * mixed origins. Nothing produces a {@code WEB} row yet — the grounded search is a later issue — and
 * a branch nobody exercises until then is a branch that rots before it is used.
 */
class AssistantProposalRoutingTest {

    private static final UUID TURN = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID WORKSPACE = UUID.randomUUID();

    private final AssistantTurnRepository turns = mock(AssistantTurnRepository.class);
    private final AssistantEventRepository events = mock(AssistantEventRepository.class);
    private final AssistantEventAppender appender = mock(AssistantEventAppender.class);
    private final ProjectAccess projectAccess = mock(ProjectAccess.class);
    private final TriageCompanyService triage = mock(TriageCompanyService.class);
    private final AuditService audit = mock(AuditService.class, RETURNS_DEEP_STUBS);
    private final ObjectMapper json = new ObjectMapper();

    private final AssistantProposalService service = new AssistantProposalService(
            turns, events, appender, projectAccess, triage, audit, json);

    @Test
    @DisplayName("universe rows go in one batch and web rows one at a time")
    void routesEachRowThroughItsOwnDoor() {
        proposalOf(universe("c1", "a1"), universe("c2", "a2"), web("c3", "Desert Technologies"));
        when(triage.addSelected(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TriageBulkAddResponse(2, 0));
        when(triage.capture(any(), any(), any(), any(), any())).thenReturn(null);

        TriageBulkAddResponse filed = service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1", "c2", "c3"), "shortlisted"), null);

        ArgumentCaptor<AddSelectedTriageCompaniesRequest> batch =
                ArgumentCaptor.forClass(AddSelectedTriageCompaniesRequest.class);
        verify(triage).addSelected(eq(USER), eq(WORKSPACE), eq(PROJECT), batch.capture(),
                eq(TriageCompanySource.ASSISTANT), any());
        assertThat(batch.getValue().apolloAccountIds()).containsExactly("a1", "a2");
        verify(triage).capture(eq(USER), eq(WORKSPACE), eq(PROJECT), any(), any());

        assertThat(filed.added()).isEqualTo(3);
        assertThat(filed.skipped()).isZero();
    }

    @Test
    @DisplayName("a company the mandate already holds is a skip, not a failed batch")
    void countsADuplicateCaptureAsSkipped() {
        proposalOf(universe("c1", "a1"), web("c2", "Desert Technologies"));
        when(triage.addSelected(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TriageBulkAddResponse(1, 0));
        when(triage.capture(any(), any(), any(), any(), any()))
                .thenThrow(ApiException.of(ErrorCode.TRIAGE_COMPANY_ALREADY_HELD));

        TriageBulkAddResponse filed = service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1", "c2"), null), null);

        // Refusing the whole accept because one of forty was a duplicate is the opposite of what
        // the two numbers are for.
        assertThat(filed.added()).isEqualTo(1);
        assertThat(filed.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("only the ticked rows are filed")
    void filesTheSubsetAndNotTheProposal() {
        proposalOf(universe("c1", "a1"), universe("c2", "a2"));
        when(triage.addSelected(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TriageBulkAddResponse(1, 0));

        service.accept(TURN, USER, WORKSPACE, new AcceptProposalRequest(List.of("c2"), null), null);

        ArgumentCaptor<AddSelectedTriageCompaniesRequest> batch =
                ArgumentCaptor.forClass(AddSelectedTriageCompaniesRequest.class);
        verify(triage).addSelected(any(), any(), any(), batch.capture(), any(), any());
        assertThat(batch.getValue().apolloAccountIds()).containsExactly("a2");
    }

    @Test
    @DisplayName("a ref the proposal never offered is refused, and nothing is written")
    void refusesARefTheProposalDoesNotHold() {
        proposalOf(universe("c1", "a1"));

        assertThatThrownBy(() -> service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1", "invented"), null), null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(triage, never()).addSelected(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the mandate authorised against is the proposal's, never the request's")
    void authorisesAgainstTheStoredMandate() {
        proposalOf(universe("c1", "a1"));
        when(triage.addSelected(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TriageBulkAddResponse(1, 0));

        service.accept(TURN, USER, WORKSPACE, new AcceptProposalRequest(List.of("c1"), null), null);

        // The accept carries no project id at all — this is the one the proposing tool call was
        // already authorised against, re-checked because membership moves.
        verify(projectAccess).requireAction(USER, WORKSPACE, PROJECT, ProjectAction.WORK_EXECUTE);
    }

    @Test
    @DisplayName("a proposal already filed is refused rather than filed again")
    void refusesASecondAccept() {
        proposalOf(universe("c1", "a1"));
        when(events.findByTurnIdAndKindInOrderBySeqAsc(eq(TURN), anyList()))
                .thenReturn(List.of(proposalEvent(universe("c1", "a1")), acceptedEvent()));

        assertThatThrownBy(() -> service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1"), null), null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ASSISTANT_PROPOSAL_ALREADY_ACCEPTED);

        verify(triage, never()).addSelected(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a turn still answering is refused, because its worker is still allocating seqs")
    void refusesWhileTheTurnIsStillWriting() {
        proposalOf(AssistantTurnStatus.RUNNING, universe("c1", "a1"));

        assertThatThrownBy(() -> service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1"), null), null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ASSISTANT_TURN_STILL_ANSWERING);

        // The card is live from the moment the proposal event reaches the browser, which is
        // mid-stream — so this is the ordinary path, not a corner of one.
        verify(triage, never()).addSelected(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an accept that loses the race to another gets the 409, not the collision")
    void answersALostRaceWithTheConflictWrittenForIt() {
        proposalOf(universe("c1", "a1"));
        when(triage.addSelected(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TriageBulkAddResponse(1, 0));
        when(appender.append(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("app_lm_assistant_event_seq_uk"));
        when(events.findByTurnIdAndKindInOrderBySeqAsc(eq(TURN), anyList()))
                .thenReturn(List.of(proposalEvent(universe("c1", "a1"))))
                .thenReturn(List.of(proposalEvent(universe("c1", "a1")), acceptedEvent()));

        assertThatThrownBy(() -> service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1"), null), null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ASSISTANT_PROPOSAL_ALREADY_ACCEPTED);
    }

    @Test
    @DisplayName("a collision with no accepted event behind it is not disguised as a conflict")
    void rethrowsACollisionThatIsNotADoubleAccept() {
        proposalOf(universe("c1", "a1"));
        when(triage.addSelected(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TriageBulkAddResponse(1, 0));
        when(appender.append(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("something else entirely"));

        assertThatThrownBy(() -> service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1"), null), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("someone else's turn answers 404, never 403")
    void hidesAnotherPersonsTurn() {
        when(turns.findByIdAndWorkspaceIdAndActorUserId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(TURN, USER, WORKSPACE,
                new AcceptProposalRequest(List.of("c1"), null), null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void proposalOf(ProposedCompany... companies) {
        proposalOf(AssistantTurnStatus.SUCCEEDED, companies);
    }

    private void proposalOf(AssistantTurnStatus status, ProposedCompany... companies) {
        AssistantTurn turn = mock(AssistantTurn.class);
        when(turn.getStatus()).thenReturn(status);
        when(turns.findByIdAndWorkspaceIdAndActorUserId(TURN, WORKSPACE, USER))
                .thenReturn(Optional.of(turn));
        when(events.findByTurnIdAndKindInOrderBySeqAsc(eq(TURN), anyList()))
                .thenReturn(List.of(proposalEvent(companies)));
    }

    private AssistantEvent proposalEvent(ProposedCompany... companies) {
        AssistantProposal proposal = new AssistantProposal(PROJECT, "Six IPPs", List.of(companies));
        return AssistantEvent.of(TURN, 1, AssistantEventKind.PROPOSAL,
                json.convertValue(proposal, new TypeReference<Map<String, Object>>() {
                }));
    }

    private AssistantEvent acceptedEvent() {
        return AssistantEvent.of(TURN, 2, AssistantEventKind.PROPOSAL_ACCEPTED,
                Map.of("status", "shortlisted", "refs", List.of("c1"), "added", 1, "skipped", 0));
    }

    private static ProposedCompany universe(String ref, String apolloAccountId) {
        return new ProposedCompany(ref, ProposalOrigin.UNIVERSE, apolloAccountId,
                "Company " + apolloAccountId, "Saudi Arabia", 400);
    }

    private static ProposedCompany web(String ref, String name) {
        return new ProposedCompany(ref, ProposalOrigin.WEB, null, name, "Saudi Arabia", null);
    }
}
