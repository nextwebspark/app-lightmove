package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

class ParallelToolCallsTest {

    private static final Set<String> READS = Set.of("searchCompanyUniverse", "lookUpCompaniesByName");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Prompt prompt = new Prompt(List.<Message>of(new UserMessage("Find utilities")));

    @AfterEach
    void stopTheExecutor() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("the reads of one round run at once, and answer in the order the model asked")
    void runsReadsSideBySide() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        RecordingTools tools = new RecordingTools(name -> {
            bothStarted.countDown();
            awaitOrFail(bothStarted);
            return name + " result";
        });
        AssistantMessage asked = asking("searchCompanyUniverse", "lookUpCompaniesByName");

        ToolExecutionResult result = new ParallelToolCalls(tools, executor, READS, 4)
                .executeToolCalls(prompt, new ChatResponse(List.of(new Generation(asked))));

        List<Message> history = result.conversationHistory();
        assertThat(history).hasSize(3);
        assertThat(history.get(1)).isSameAs(asked);
        assertThat(((ToolResponseMessage) history.get(2)).getResponses())
                .extracting(ToolResponseMessage.ToolResponse::name, ToolResponseMessage.ToolResponse::responseData)
                .containsExactly(tuple("searchCompanyUniverse", "searchCompanyUniverse result"),
                        tuple("lookUpCompaniesByName", "lookUpCompaniesByName result"));
    }

    @Test
    @DisplayName("a round that proposes the card runs in the model's order, as one batch")
    void keepsARoundWithTheCardInOrder() {
        RecordingTools tools = new RecordingTools(name -> name + " result");
        ChatResponse response = new ChatResponse(List.of(new Generation(
                asking("searchCompanyUniverse", "proposeCompanies"))));

        new ParallelToolCalls(tools, executor, READS, 4).executeToolCalls(prompt, response);

        assertThat(tools.batches.get()).isEqualTo(1);
        assertThat(tools.lastBatchSize).isEqualTo(2);
    }

    @Test
    @DisplayName("a tool that fails beside another fails the round once both have finished")
    void failsTheRoundAfterEveryToolFinished() {
        AtomicInteger finished = new AtomicInteger();
        RecordingTools tools = new RecordingTools(name -> {
            finished.incrementAndGet();
            if (name.equals("searchCompanyUniverse")) {
                throw new IllegalStateException("search failed");
            }
            return name + " result";
        });
        ChatResponse response = new ChatResponse(List.of(new Generation(
                asking("searchCompanyUniverse", "lookUpCompaniesByName"))));

        assertThatThrownBy(() -> new ParallelToolCalls(tools, executor, READS, 4).executeToolCalls(prompt, response))
                .isInstanceOf(IllegalStateException.class).hasMessage("search failed");

        assertThat(finished.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("side-by-side tools never outnumber the permits every ask shares")
    void holdsToolsToTheSharedPermits() {
        AtomicInteger atOnce = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        RecordingTools tools = new RecordingTools(name -> {
            most.accumulateAndGet(atOnce.incrementAndGet(), Math::max);
            pause();
            atOnce.decrementAndGet();
            return name + " result";
        });
        ChatResponse response = new ChatResponse(List.of(new Generation(asking(
                "searchCompanyUniverse", "lookUpCompaniesByName", "searchCompanyUniverse", "lookUpCompaniesByName"))));

        new ParallelToolCalls(tools, executor, READS, 2).executeToolCalls(prompt, response);

        assertThat(most.get()).isLessThanOrEqualTo(2);
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static AssistantMessage asking(String... toolNames) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        for (String name : toolNames) {
            calls.add(new AssistantMessage.ToolCall("call-" + name, "function", name, "{}"));
        }
        return AssistantMessage.builder().toolCalls(calls).build();
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the tools did not run side by side");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /** Stands in for Spring AI's manager: answers each tool call of the batch it is handed. */
    private static final class RecordingTools implements ToolCallingManager {

        private final Function<String, String> answer;
        private final AtomicInteger batches = new AtomicInteger();
        private volatile int lastBatchSize;

        private RecordingTools(Function<String, String> answer) {
            this.answer = answer;
        }

        @Override
        public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
            return List.of();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            AssistantMessage asked = chatResponse.getResult().getOutput();
            batches.incrementAndGet();
            lastBatchSize = asked.getToolCalls().size();
            List<ToolResponseMessage.ToolResponse> responses = asked.getToolCalls().stream()
                    .map(call -> new ToolResponseMessage.ToolResponse(call.id(), call.name(), answer.apply(call.name())))
                    .toList();
            List<Message> history = new ArrayList<>(prompt.getInstructions());
            history.add(asked);
            history.add(ToolResponseMessage.builder().responses(responses).build());
            return ToolExecutionResult.builder().conversationHistory(history).returnDirect(false).build();
        }
    }
}
