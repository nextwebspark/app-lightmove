package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;

/** The answer streams piece by piece, with the tools run between the model's rounds. */
class AssistantModelCallTest {

    private final IndustryAdjacency adjacency = mock(IndustryAdjacency.class);
    private final List<AssistantStepEvent> steps = new ArrayList<>();
    private final TurnRecorder recorder = new TurnRecorder(steps::add);
    private final AssistantToolContext context =
            new AssistantToolContext(UUID.randomUUID(), UUID.randomUUID(), recorder);

    @Test
    @DisplayName("a tool round runs its tool, then the answer arrives as the pieces the model wrote")
    void streamsTheAnswerAfterTheTools() {
        when(adjacency.neighboursOf("banking")).thenReturn(List.of("insurance"));
        ScriptedModel model = new ScriptedModel(true, 0);
        List<String> pieces = new ArrayList<>();

        String answer = call(model).answer("Banks next door?", List.of(), facts(), context, pieces::add);

        assertThat(pieces).containsExactly("Insurance ", "sits next to banking.");
        assertThat(answer).isEqualTo("Insurance sits next to banking.");
        assertThat(recorder.steps()).singleElement().satisfies(step ->
                assertThat(step.label()).isEqualTo("Finding sectors next to banking"));
        assertThat(model.lastPrompt.getSystemMessage().getText())
                .contains("FIRM BLOCK").contains("POSITION BLOCK");
    }

    @Test
    @DisplayName("a failure before anything was shown is tried once more")
    void retriesAFailureBeforeAnythingWasShown() {
        ScriptedModel model = new ScriptedModel(false, 1);

        String answer = call(model).answer("Hello", List.of(), facts(), context, piece -> { });

        assertThat(answer).isEqualTo("Insurance sits next to banking.");
        assertThat(model.calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a failure after a tool ran is not retried, because the tool would run and bill twice")
    void doesNotRetryOnceAToolRan() {
        when(adjacency.neighboursOf("banking")).thenReturn(List.of());
        ScriptedModel model = new ScriptedModel(true, 0).failingAfterTools();

        assertThatThrownBy(() -> call(model).answer("Hello", List.of(), facts(), context, piece -> { }))
                .isInstanceOf(ApiException.class)
                .extracting(failed -> ((ApiException) failed).getCode())
                .isEqualTo(ErrorCode.ASSISTANT_UNAVAILABLE);
        assertThat(model.calls.get()).isEqualTo(2);
    }

    private AssistantModelCall call(ChatModel model) {
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().model()).thenReturn("gemini-2.5-flash");
        when(properties.assistant().historyWindow()).thenReturn(12);
        return new AssistantModelCall(ChatClient.builder(model).build(), mock(CompanySearchTools.class),
                mock(ProposalTools.class), new SectorTools(adjacency), mock(NamedCompanyTools.class),
                new ByteArrayResource("Firm: {firm}\nPosition: {position}".getBytes()), properties);
    }

    private static AssistantPromptFacts facts() {
        return new AssistantPromptFacts("FIRM BLOCK", "POSITION BLOCK");
    }

    /** Asks for adjacentIndustries first when told to, then writes its answer in two pieces. */
    private static final class ScriptedModel implements ChatModel {

        private final boolean callsATool;
        private final AtomicInteger failuresLeft;
        private final AtomicInteger calls = new AtomicInteger();
        private boolean failAfterTools;
        private volatile Prompt lastPrompt;

        ScriptedModel(boolean callsATool, int failuresFirst) {
            this.callsATool = callsATool;
            this.failuresLeft = new AtomicInteger(failuresFirst);
        }

        ScriptedModel failingAfterTools() {
            failAfterTools = true;
            return this;
        }

        /** What Gemini reports: the client merges a call's options onto these, and only tool-calling options run tools. */
        @Override
        public ChatOptions getOptions() {
            return GoogleGenAiChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("the assistant streams");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            calls.incrementAndGet();
            lastPrompt = prompt;
            if (failuresLeft.getAndDecrement() > 0) {
                return Flux.error(new IllegalStateException("Vertex hiccup"));
            }
            boolean toolsAnswered = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
            if (callsATool && !toolsAnswered) {
                AssistantMessage toolCall = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function",
                                "adjacentIndustries", "{\"industry\":\"Banking\"}")))
                        .build();
                return Flux.just(new ChatResponse(List.of(new Generation(toolCall))));
            }
            if (failAfterTools) {
                return Flux.error(new IllegalStateException("Vertex hiccup after the tools"));
            }
            return Flux.just(text("Insurance "), text("sits next to banking."));
        }

        private static ChatResponse text(String piece) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(piece))));
        }
    }
}
