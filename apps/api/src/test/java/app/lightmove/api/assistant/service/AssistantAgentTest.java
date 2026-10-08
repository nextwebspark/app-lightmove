package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.HiringSide;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.CandidateTools;
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.MandateBrief;
import app.lightmove.api.assistant.tool.MandateTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.Firm;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.ObjectMapper;

class AssistantAgentTest {

    private final AssistantModelCall model = mock(AssistantModelCall.class);
    private final MandateTools mandateTools = mock(MandateTools.class);
    private final HiringSideResolver hiringSides = mock(HiringSideResolver.class);
    private final ProposalTools proposalTools = mock(ProposalTools.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });
    private final AssistantToolContext context = new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(),
            recorder);
    private final AssistantToolset toolset = new AssistantToolset(AssistantSkills.fromClasspath(), new ObjectMapper(),
            mandateTools, mock(CompanySearchTools.class), mock(NamedCompanyTools.class), mock(SectorTools.class),
            proposalTools, mock(CandidateTools.class));
    private final AssistantAgent agent = new AssistantAgent(model, toolset, mandateTools, proposalTools, hiringSides);

    private static final String TWO_QUESTIONS = """
            {"questions":[
              {"question":"Which markets should the companies operate in?","header":"Region","multiSelect":false,
               "options":[{"label":"GCC only (Recommended)","description":"The six Gulf states"},
                          {"label":"MENA","description":"Adds Egypt, Jordan and Morocco"}]},
              {"question":"Which ownership types?","header":"Ownership","multiSelect":true,
               "options":[{"label":"Listed","description":"On a public exchange"},
                          {"label":"Family-owned","description":"Private family groups"}]}]}
            """;

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
    @DisplayName("the model is offered the playbooks, the question tool and the assistant's own tools, and nothing that reaches the host")
    void offersOnlyTheAssistantsTools() {
        List<String> offered = toolset.forAsk(recorder).stream()
                .map(tool -> tool.getToolDefinition().name())
                .toList();

        assertThat(offered).containsExactlyInAnyOrder(AssistantSkills.TOOL_NAME, "AskUserQuestionTool",
                "readMandateBrief", "searchCompanyUniverse", "describeMarket", "lookUpCompaniesByName",
                "adjacentIndustries", "proposeCompanies",
                "listMappedExecutives", "readExecutiveProfile", "companiesWithoutExecutives");
    }

    private ToolCallback questionTool() {
        return toolset.forAsk(recorder).stream()
                .filter(tool -> tool.getToolDefinition().name().equals("AskUserQuestionTool"))
                .findFirst()
                .orElseThrow();
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
    @DisplayName("asking ends the loop: the question tool returns directly and is never offered an answers slot")
    void theQuestionToolEndsTheAnswer() {
        ToolCallback ask = questionTool();

        assertThat(ask.getToolMetadata().returnDirect()).isTrue();
        assertThat(new ObjectMapper().readTree(ask.getToolDefinition().inputSchema()).path("properties")
                .has("answers")).isFalse();
    }

    @Test
    @DisplayName("the questions the model asks are kept for the card, and the tool answers at once")
    void recordsTheQuestionsAsked() {
        String result = questionTool().call(TWO_QUESTIONS);

        assertThat(result).isEqualTo(AskUserQuestionCallback.SHOWN_TO_CONSULTANT);
        assertThat(recorder.questions()).hasSize(2);
        assertThat(recorder.questions().getFirst().header()).isEqualTo("Region");
        assertThat(recorder.questions().getFirst().options()).extracting("label")
                .containsExactly("GCC only (Recommended)", "MENA");
        assertThat(recorder.questions().get(1).multiSelect()).isTrue();
        assertThat(recorder.steps()).extracting("label").containsExactly("Asking you some questions");
    }

    @Test
    @DisplayName("an answer that asked shows its lead-in and suggests no companies")
    void anAnswerThatAskedSuggestsNothing() {
        when(model.ask(anyMap(), anyList(), anyList(), anyString(), any())).thenAnswer(call -> {
            questionTool().call(TWO_QUESTIONS);
            return AskUserQuestionCallback.SHOWN_TO_CONSULTANT;
        });

        assertThat(agent.answer("Find me companies", List.of(), context)).isEqualTo(AssistantAgent.ASKED_LEAD_IN);

        verify(proposalTools, never()).proposeWhatWasFound(any());
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
}
