package app.uncava.api.assistant.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Runs the tools one model round asked for side by side instead of one after another, so a round costs
 * its slowest tool rather than their sum. Only where every tool of the round is a read named as safe to
 * run beside another; a round with any other tool runs in the order the model gave. Every ask of the
 * instance shares one set of permits, so side-by-side tools never outnumber the connection pool.
 */
class ParallelToolCalls implements ToolCallingManager {

    private final ToolCallingManager oneAtATime;
    private final Executor executor;
    private final Set<String> readsSafeSideBySide;
    private final Semaphore running;

    ParallelToolCalls(ToolCallingManager oneAtATime, Executor executor, Set<String> readsSafeSideBySide,
                      int toolsAtOnce) {
        this.oneAtATime = oneAtATime;
        this.executor = executor;
        this.readsSafeSideBySide = readsSafeSideBySide;
        this.running = new Semaphore(toolsAtOnce);
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return oneAtATime.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        AssistantMessage asked = chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .filter(output -> !output.getToolCalls().isEmpty())
                .findFirst()
                .orElse(null);
        if (asked == null || asked.getToolCalls().size() < 2
                || !asked.getToolCalls().stream().allMatch(call -> readsSafeSideBySide.contains(call.name()))) {
            return holdingAPermit(prompt, chatResponse);
        }
        List<CompletableFuture<ToolExecutionResult>> tools = asked.getToolCalls().stream()
                .map(call -> CompletableFuture.supplyAsync(() -> holdingAPermit(prompt, askingOnly(call)), executor))
                .toList();
        return merged(prompt, asked, tools);
    }

    private ToolExecutionResult holdingAPermit(Prompt prompt, ChatResponse chatResponse) {
        running.acquireUninterruptibly();
        try {
            return oneAtATime.executeToolCalls(prompt, chatResponse);
        } finally {
            running.release();
        }
    }

    /** Every tool is waited for before the first failure is thrown, so none is left running behind the answer. */
    private static ToolExecutionResult merged(Prompt prompt, AssistantMessage asked,
                                              List<CompletableFuture<ToolExecutionResult>> tools) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        boolean returnDirect = true;
        RuntimeException firstFailure = null;
        for (CompletableFuture<ToolExecutionResult> tool : tools) {
            try {
                ToolExecutionResult result = tool.join();
                returnDirect &= result.returnDirect();
                if (result.conversationHistory().getLast() instanceof ToolResponseMessage answered) {
                    responses.addAll(answered.getResponses());
                }
            } catch (CompletionException failed) {
                if (firstFailure == null) {
                    firstFailure = failed.getCause() instanceof RuntimeException cause ? cause : failed;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        List<Message> history = new ArrayList<>(prompt.getInstructions());
        history.add(asked);
        history.add(ToolResponseMessage.builder().responses(responses).build());
        return ToolExecutionResult.builder().conversationHistory(history).returnDirect(returnDirect).build();
    }

    private static ChatResponse askingOnly(AssistantMessage.ToolCall call) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build())));
    }
}
