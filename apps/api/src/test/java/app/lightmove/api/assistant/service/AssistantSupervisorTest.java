package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;

class AssistantSupervisorTest {

    private final AssistantModelCall model = mock(AssistantModelCall.class);
    private final TurnRecorder recorder = new TurnRecorder(step -> { });
    private final AssistantToolContext context = new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(),
            recorder);

    /** Both shipped specialists need a position, so today this path waits for one gated more narrowly. */
    @Test
    void aLoneAvailableSpecialistAnswersWithoutASupervisorRound() {
        FakeSpecialist companies = new FakeSpecialist("companies", true);
        FakeSpecialist candidates = new FakeSpecialist("candidates", false);
        when(model.askSpecialist(eq(companies), anyList(), eq("Find utilities"), eq(context)))
                .thenReturn("Here are four");

        String answer = supervisor(companies, candidates).answer("Find utilities", List.of(), context);

        assertThat(answer).isEqualTo("Here are four");
        assertThat(companies.events).containsExactly("prepare", "afterAnswer");
        assertThat(candidates.events).isEmpty();
        assertThat(recorder.consultedSpecialists()).containsExactly("companies");
        verify(model, never()).askSupervisor(anyList(), anyList(), anyString(), any());
    }

    @Test
    void severalAvailableSpecialistsAreOfferedToTheSupervisorAsTools() {
        FakeSpecialist companies = new FakeSpecialist("companies", true);
        FakeSpecialist candidates = new FakeSpecialist("candidates", true);
        FakeSpecialist reports = new FakeSpecialist("reports", false);
        List<String> offered = new ArrayList<>();
        when(model.askSupervisor(anyList(), anyList(), eq("Who is mapped?"), eq(context))).thenAnswer(call -> {
            List<SpecialistToolCallback> tools = call.getArgument(0);
            tools.forEach(tool -> offered.add(tool.getToolDefinition().name()));
            return "Three executives";
        });

        String answer = supervisor(companies, candidates, reports).answer("Who is mapped?", List.of(), context);

        assertThat(offered).containsExactly("askCompaniesSpecialist", "askCandidatesSpecialist");
        assertThat(answer).isEqualTo("Three executives");
        assertThat(companies.events).containsExactly("prepare");
        assertThat(reports.events).isEmpty();
    }

    @Test
    void aFailedModelCallIsReportedAsUnavailable() {
        FakeSpecialist companies = new FakeSpecialist("companies", true);
        when(model.askSpecialist(any(), anyList(), anyString(), any())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> supervisor(companies).answer("Find utilities", List.of(), context))
                .isInstanceOfSatisfying(ApiException.class,
                        failed -> assertThat(failed.getCode()).isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE));
        assertThat(companies.events).containsExactly("prepare");
    }

    @Test
    void aSpecialistToolRunsUnderTheServersContextAndRecordsItsStep() {
        FakeSpecialist candidates = new FakeSpecialist("candidates", true);
        when(model.askSpecialist(eq(candidates), anyList(), anyString(), eq(context))).thenReturn("Two at Aramco");
        SpecialistToolCallback tool = new SpecialistToolCallback(candidates, model, new ObjectMapper(), List.of(),
                "Who is mapped at Aramco?", 3);

        String answer = tool.call("{\"task\":\"List executives at Aramco\"}", new ToolContext(context.asMap()));

        assertThat(answer).isEqualTo("{\"answer\":\"Two at Aramco\"}");
        verify(model).askSpecialist(candidates, List.of(),
                "Who is mapped at Aramco?\n\n(Focus: List executives at Aramco)", context);
        assertThat(recorder.steps()).singleElement()
                .satisfies(step -> assertThat(step.label()).isEqualTo("Asking the candidates specialist"));
        assertThat(recorder.consultedSpecialists()).containsExactly("candidates");
        assertThat(candidates.events).containsExactly("afterAnswer");
    }

    @Test
    void aSpecialistToolRefusesPastTheCallCapAndWhenNotAvailable() {
        FakeSpecialist candidates = new FakeSpecialist("candidates", true);
        FakeSpecialist reports = new FakeSpecialist("reports", false);
        recorder.consulted("companies");
        ToolContext toolContext = new ToolContext(context.asMap());

        String capped = new SpecialistToolCallback(candidates, model, new ObjectMapper(), List.of(), "q", 1)
                .call("{\"task\":\"t\"}", toolContext);
        String refused = new SpecialistToolCallback(reports, model, new ObjectMapper(), List.of(), "q", 3)
                .call("{\"task\":\"t\"}", toolContext);

        assertThat(capped).startsWith("{\"answer\":\"No more specialists");
        assertThat(refused).contains("not available");
        verify(model, never()).askSpecialist(any(), anyList(), anyString(), any());
    }

    @Test
    void aSpecialistAskedTwiceInOneAskAnswersItsFirstReplyWithoutASecondCall() {
        FakeSpecialist companies = new FakeSpecialist("companies", true);
        when(model.askSpecialist(eq(companies), anyList(), anyString(), eq(context))).thenReturn("Four utilities");
        SpecialistToolCallback tool = new SpecialistToolCallback(companies, model, new ObjectMapper(), List.of(),
                "Find utilities", 3);
        ToolContext toolContext = new ToolContext(context.asMap());

        tool.call("{\"task\":\"Find utilities\"}", toolContext);
        String again = tool.call("{\"task\":\"Find more utilities\"}", toolContext);

        assertThat(again).isEqualTo("{\"answer\":\"Four utilities\"}");
        verify(model, times(1)).askSpecialist(any(), anyList(), anyString(), any());
        assertThat(recorder.consultedSpecialists()).containsExactly("companies");
    }

    @Test
    void aMalformedOrOverlongTaskIsDroppedOrCutRatherThanFailingTheAsk() {
        FakeSpecialist candidates = new FakeSpecialist("candidates", true);
        when(model.askSpecialist(any(), anyList(), anyString(), any())).thenReturn("ok");
        ToolContext toolContext = new ToolContext(context.asMap());

        new SpecialistToolCallback(candidates, model, new ObjectMapper(), List.of(), "Who is mapped?", 3)
                .call("{\"task\": not json", toolContext);
        new SpecialistToolCallback(candidates, model, new ObjectMapper(), List.of(), "Who else?", 3)
                .call("{\"task\":\"" + "x".repeat(SpecialistToolCallback.MAX_TASK_LENGTH + 50) + "\"}",
                        new ToolContext(new AssistantToolContext(context.workspaceId(), context.projectId(),
                                new TurnRecorder(step -> { })).asMap()));

        verify(model).askSpecialist(candidates, List.of(), "Who is mapped?", context);
        verify(model).askSpecialist(eq(candidates), anyList(),
                eq("Who else?\n\n(Focus: " +"x".repeat(SpecialistToolCallback.MAX_TASK_LENGTH) + ")"), any());
    }

    private AssistantSupervisor supervisor(AssistantSpecialist... specialists) {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.assistant()).thenReturn(new AssistantSettings("gemini-2.5-flash", 0.2, 512, 12, 25, 250,
                4, 10, 5, Duration.ofSeconds(25), 15, 3, 50, 75, List.of()));
        return new AssistantSupervisor(List.of(specialists), model, new ObjectMapper(), properties);
    }

    /** Records which hooks ran. */
    private static final class FakeSpecialist implements AssistantSpecialist {

        private final String domain;
        private final boolean available;
        private final List<String> events = new ArrayList<>();

        private FakeSpecialist(String domain, boolean available) {
            this.domain = domain;
            this.available = available;
        }

        @Override
        public String toolName() {
            return "ask" + Character.toUpperCase(domain.charAt(0)) + domain.substring(1) + "Specialist";
        }

        @Override
        public String domain() {
            return domain;
        }

        @Override
        public String description() {
            return "Answers about " + domain;
        }

        @Override
        public boolean availableTo(AssistantToolContext context) {
            return available;
        }

        @Override
        public String promptId() {
            return "assistant-" + domain;
        }

        @Override
        public Resource systemPrompt() {
            return new ByteArrayResource(new byte[0]);
        }

        @Override
        public Map<String, Object> systemParams(AssistantToolContext context) {
            return Map.of();
        }

        @Override
        public List<Object> tools() {
            return List.of();
        }

        @Override
        public void prepare(List<AssistantTurn> history, TurnRecorder recorder) {
            events.add("prepare");
        }

        @Override
        public void afterAnswer(AssistantToolContext context) {
            events.add("afterAnswer");
        }
    }
}
