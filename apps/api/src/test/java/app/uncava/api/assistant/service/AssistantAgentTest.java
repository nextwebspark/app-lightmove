package app.uncava.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.uncava.api.assistant.model.AssistantProposal;
import app.uncava.api.assistant.model.AssistantRoundFailed;
import app.uncava.api.assistant.model.HiringSide;
import app.uncava.api.assistant.model.ProposedCompany;
import app.uncava.api.assistant.tool.AssistantToolContext;
import app.uncava.api.assistant.tool.CandidateTools;
import app.uncava.api.assistant.tool.CompanyDetailTools;
import app.uncava.api.assistant.tool.CompanyDiscoveryTools;
import app.uncava.api.assistant.tool.CompanySearchTools;
import app.uncava.api.assistant.tool.MandateBrief;
import app.uncava.api.assistant.tool.MandateTools;
import app.uncava.api.assistant.tool.NamedCompanyTools;
import app.uncava.api.assistant.tool.ProposalTools;
import app.uncava.api.assistant.tool.SectorTools;
import app.uncava.api.assistant.tool.TurnRecorder;
import app.uncava.api.common.persona.model.HiringCompanyProfile;
import app.uncava.api.common.persona.model.HiringPersona;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.workspace.constant.WorkspaceMode;
import app.uncava.api.workspace.model.Firm;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.ObjectMapper;

class AssistantAgentTest {

    private static final String TWO_QUESTIONS = """
            {"questions":[
              {"question":"Which markets should the companies operate in?","header":"Region","multiSelect":false,
               "options":[{"label":"GCC only (Recommended)","description":"The six Gulf states"},
                          {"label":"MENA","description":"Adds Egypt, Jordan and Morocco"}]},
              {"question":"Which ownership types?","header":"Ownership","multiSelect":true,
               "options":[{"label":"Listed","description":"On a public exchange"},
                          {"label":"Family-owned","description":"Private family groups"}]}]}
            """;

    private static final String QUESTION_WITH_ONE_OPTION = """
            {"questions":[{"question":"Which markets?","header":"Region",
              "options":[{"label":"GCC only","description":"The six Gulf states"}]}]}
            """;

    private static final String QUESTION_WITH_A_BLANK_HEADER = """
            {"questions":[{"question":"Which markets?","header":" ",
              "options":[{"label":"GCC only","description":"The six Gulf states"},
                         {"label":"MENA","description":"Adds Egypt"}]}]}
            """;

    private final AssistantModelCall model = mock(AssistantModelCall.class);
    private final MandateTools mandateTools = mock(MandateTools.class);
    private final HiringSideResolver hiringSides = mock(HiringSideResolver.class);
    private final ProposalTools proposalTools = mock(ProposalTools.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });
    private final AssistantToolContext context = new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(),
            recorder);
    private final AssistantToolset toolset = new AssistantToolset(AssistantSkills.fromClasspath(), new ObjectMapper(),
            mandateTools, mock(CompanySearchTools.class), mock(NamedCompanyTools.class),
            mock(CompanyDiscoveryTools.class), mock(SectorTools.class),
            proposalTools, mock(CandidateTools.class), mock(CompanyDetailTools.class));
    private final AssistantAgent agent = new AssistantAgent(model, toolset, mandateTools, proposalTools, hiringSides);

    @BeforeEach
    void aMandateWithAFirm() {
        HiringCompanyProfile firm = new HiringCompanyProfile("Kalem Group", null, null, null, null, null,
                HiringPersona.empty());
        when(hiringSides.resolve(context.workspaceId(), context.projectId()))
                .thenReturn(new HiringSide(new Firm(WorkspaceMode.COMPANY, firm), firm));
        when(mandateTools.briefOf(context.workspaceId(), context.projectId())).thenReturn(new MandateBrief(
                "Head of Retail", null, null, null, null, List.of(), null, null, null, List.of()));
    }

    @Test
    @DisplayName("the model is offered the playbooks, the question tool and the assistant's own tools, "
            + "and nothing that reaches the host")
    void offersOnlyTheAssistantsTools() {
        List<String> offered = toolset.forAsk(recorder).stream()
                .map(tool -> tool.getToolDefinition().name())
                .toList();

        assertThat(offered).containsExactlyInAnyOrder(AssistantSkills.TOOL_NAME, "AskUserQuestionTool",
                "readMandateBrief", "searchCompanyUniverse", "describeMarket", "lookUpCompaniesByName",
                "identifyCompany", "findSimilarCompanies", "searchCompaniesByActivity",
                "adjacentIndustries", "proposeCompanies",
                "listMappedExecutives", "readExecutiveProfile", "companiesWithoutExecutives",
                "readCompanyDetails", "listMandateCompanies");
    }

    @Test
    @DisplayName("only tools the model is offered run side by side, and never the playbook, a question or the card")
    void runsOnlyReadsSideBySide() {
        List<String> offered = toolset.forAsk(recorder).stream()
                .map(tool -> tool.getToolDefinition().name())
                .toList();

        assertThat(offered).containsAll(AssistantToolset.READS_SAFE_SIDE_BY_SIDE);
        assertThat(AssistantToolset.READS_SAFE_SIDE_BY_SIDE)
                .doesNotContain(AssistantSkills.TOOL_NAME, "AskUserQuestionTool", "proposeCompanies");
        assertThat(offered).hasSize(AssistantToolset.READS_SAFE_SIDE_BY_SIDE.size() + 3);
    }

    @Test
    @DisplayName("loading a playbook is a step of the answer, and recorded once however often it is loaded")
    void recordsEachPlaybookLoaded() {
        ToolCallback skill = toolset.forAsk(recorder).stream()
                .filter(tool -> tool.getToolDefinition().name().equals(AssistantSkills.TOOL_NAME))
                .findFirst()
                .orElseThrow();

        skill.call("{\"command\":\"find-companies\"}");
        skill.call("{\"command\":\"find-companies\"}");
        skill.call("{\"command\":\"not-a-playbook\"}");

        assertThat(recorder.skillsUsed()).containsExactly("find-companies");
        assertThat(recorder.steps()).hasSize(2).allSatisfy(step -> {
            assertThat(step.label()).isEqualTo("Following the find companies playbook");
            assertThat(step.detail()).isNull();
        });
    }

    @Test
    @DisplayName("an answer that found companies but suggested none still suggests them")
    void suggestsWhatWasFoundAfterAnswering() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any())).thenReturn("Four utilities");

        assertThat(agent.answer("Find utilities", List.of(), context)).isEqualTo("Four utilities");

        verify(proposalTools).proposeWhatWasFound(context);
    }

    @Test
    @DisplayName("the question tool is never offered an answers slot, so the model cannot answer its own questions")
    void offersTheQuestionToolNoAnswersSlot() {
        ToolCallback ask = questionTool();

        assertThat(new ObjectMapper().readTree(ask.getToolDefinition().inputSchema()).path("properties")
                .has("answers")).isFalse();
        assertThat(ask.getToolMetadata().returnDirect()).isFalse();
    }

    @Test
    @DisplayName("the questions the model asks are kept for the card, and the tool answers at once")
    void recordsTheQuestionsAsked() {
        recorder.usedSkill("find-companies");

        String result = questionTool().call(TWO_QUESTIONS);

        assertThat(result).isEqualTo(asJson(AskUserQuestionCallback.SHOWN_TO_CONSULTANT));
        assertThat(recorder.questions()).hasSize(2);
        assertThat(recorder.questions().getFirst().header()).isEqualTo("Region");
        assertThat(recorder.questions().getFirst().options()).extracting("label")
                .containsExactly("GCC only (Recommended)", "MENA");
        assertThat(recorder.questions().get(1).multiSelect()).isTrue();
        assertThat(recorder.steps()).extracting("label").containsExactly("Asking you some questions");
    }

    @Test
    @DisplayName("a question asked before any playbook is turned away: the playbook may settle it")
    void turnsAwayAQuestionBeforeAPlaybook() {
        assertThat(questionTool().call(TWO_QUESTIONS)).isEqualTo(asJson(AskUserQuestionCallback.LOAD_A_PLAYBOOK_FIRST));

        assertThat(recorder.askedQuestions()).isFalse();
    }

    @Test
    @DisplayName("a question asked after companies were suggested is turned away, so no card sits under the list")
    void turnsAwayAQuestionAfterSuggestingCompanies() {
        recorder.usedSkill("find-companies");
        recorder.propose(new AssistantProposal("Two utilities", List.of(), Map.of()));

        assertThat(questionTool().call(TWO_QUESTIONS)).isEqualTo(asJson(AskUserQuestionCallback.ALREADY_SUGGESTED));

        assertThat(recorder.askedQuestions()).isFalse();
    }

    @Test
    @DisplayName("an answer that asked shows its lead-in and suggests no companies")
    void anAnswerThatAskedSuggestsNothing() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any())).thenAnswer(call -> {
            recorder.usedSkill("find-companies");
            questionTool().call(TWO_QUESTIONS);
            return AskUserQuestionCallback.SHOWN_TO_CONSULTANT;
        });

        assertThat(agent.answer("Find me companies", List.of(), context)).isEqualTo(AssistantAgent.ASKED_LEAD_IN);

        verify(proposalTools, never()).proposeWhatWasFound(any());
    }

    @Test
    @DisplayName("a question the card cannot draw is not shown, and the model is told so")
    void reportsQuestionsThatReachedNobody() {
        recorder.usedSkill("find-companies");

        assertThat(questionTool().call(QUESTION_WITH_ONE_OPTION)).isEqualTo(asJson(AskUserQuestionCallback.NOTHING_SHOWN));
        assertThat(questionTool().call(QUESTION_WITH_A_BLANK_HEADER))
                .isEqualTo(asJson(AskUserQuestionCallback.NOTHING_SHOWN));
        assertThat(questionTool().call("not json")).isEqualTo(asJson(AskUserQuestionCallback.NOTHING_SHOWN));

        assertThat(recorder.askedQuestions()).isFalse();
        assertThat(recorder.steps()).isEmpty();
    }

    @Test
    @DisplayName("a failed model call is reported as the assistant being unavailable")
    void reportsAFailedModelCallAsUnavailable() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any()))
                .thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> agent.answer("Find utilities", List.of(), context))
                .isInstanceOfSatisfying(ApiException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
    }

    @Test
    @DisplayName("a round that failed after the tools found companies still answers with them")
    void answersWithWhatWasFoundBeforeTheFailure() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any())).thenThrow(roundFailed());
        doAnswer(call -> {
            recorder.propose(new AssistantProposal("Companies found", List.of(utility()), Map.of()));
            return null;
        }).when(proposalTools).proposeWhatWasFound(context);

        assertThat(agent.answer("Find utilities", List.of(), context))
                .isEqualTo(AssistantAgent.STOPPED_EARLY_LEAD_IN);
    }

    @Test
    @DisplayName("a round that failed before anything was found is the assistant being unavailable")
    void reportsARoundThatFoundNothingAsUnavailable() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any())).thenThrow(roundFailed());

        assertThatThrownBy(() -> agent.answer("Find utilities", List.of(), context))
                .isInstanceOfSatisfying(ApiException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
    }

    @Test
    @DisplayName("a round that failed after questions were asked still shows the questions")
    void keepsTheQuestionsAskedBeforeTheFailure() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any())).thenAnswer(call -> {
            recorder.usedSkill("find-companies");
            questionTool().call(TWO_QUESTIONS);
            throw roundFailed();
        });

        assertThat(agent.answer("Find me companies", List.of(), context)).isEqualTo(AssistantAgent.ASKED_LEAD_IN);

        verify(proposalTools, never()).proposeWhatWasFound(any());
    }

    @Test
    @DisplayName("a failure that is not a model round's is never answered with a partial card")
    void doesNotAnswerThroughAnUnexpectedFailure() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any()))
                .thenThrow(new IllegalStateException("a bug"));

        assertThatThrownBy(() -> agent.answer("Find utilities", List.of(), context))
                .isInstanceOf(ApiException.class);

        verify(proposalTools, never()).proposeWhatWasFound(any());
    }

    private static AssistantRoundFailed roundFailed() {
        return new AssistantRoundFailed("Round 3 failed", new IllegalStateException("down"));
    }

    private static ProposedCompany utility() {
        return new ProposedCompany("acc-1", null, "Gulf Utilities", "United Arab Emirates", 1200, null, null, null);
    }

    private ToolCallback questionTool() {
        return toolset.forAsk(recorder).stream()
                .filter(tool -> tool.getToolDefinition().name().equals("AskUserQuestionTool"))
                .findFirst()
                .orElseThrow();
    }

    private static String asJson(String reply) {
        return new ObjectMapper().writeValueAsString(reply);
    }
}
