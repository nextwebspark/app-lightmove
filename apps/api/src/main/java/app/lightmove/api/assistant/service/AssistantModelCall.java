package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.service.ChatCallLog;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/** The one model conversation an ask is: the firm and the position in the prompt, the tools, and the answer. */
@Slf4j
@Component
class AssistantModelCall {

    static final String PROMPT_ID = "assistant-turn";

    private static final Duration RETRY_AFTER = Duration.ofMillis(500);

    private final ChatClient chatClient;
    private final CompanySearchTools searchTools;
    private final ProposalTools proposalTools;
    private final SectorTools sectorTools;
    private final NamedCompanyTools namedCompanyTools;
    private final Resource systemPrompt;
    private final AssistantSettings settings;

    AssistantModelCall(ChatClient chatClient, CompanySearchTools searchTools, ProposalTools proposalTools,
                       SectorTools sectorTools, NamedCompanyTools namedCompanyTools,
                       @Value("classpath:prompts/assistant-system.st") Resource systemPrompt,
                       LightMoveProperties properties) {
        this.chatClient = chatClient;
        this.searchTools = searchTools;
        this.proposalTools = proposalTools;
        this.sectorTools = sectorTools;
        this.namedCompanyTools = namedCompanyTools;
        this.systemPrompt = systemPrompt;
        this.settings = properties.assistant();
    }

    /**
     * Deliberately not {@code LlmCallPolicy.forPrompt}: its SafeGuardAdvisor refuses text matching a
     * phrase list, which is right for a spreadsheet header and wrong for conversation — "ignore the
     * declined ones" would be refused, and a tool result quoting one of those phrases would block the
     * turn. #429 owns what replaces it. The ChatCallLog attribution is kept, since that is what keeps
     * prompt and answer content out of the logs.
     *
     * <p>Streamed, so the answer's text reaches the panel as it is written; the tools still run between
     * rounds inside the stream. The saved answer is exactly the text that was streamed. A stream is not
     * retried by Spring AI the way a call is, so it is retried here once — but only while nothing has
     * been shown, because a retry after a tool ran would repeat billed lookups and redraw its steps.
     */
    String answer(String question, List<AssistantTurn> history, AssistantPromptFacts facts,
                  AssistantToolContext context, Consumer<String> onAnswer) {
        StringBuilder answer = new StringBuilder();
        try {
            Flux.defer(() -> chatClient.prompt()
                            .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, PROMPT_ID))
                            .options(GoogleGenAiChatOptions.builder()
                                    .model(settings.model())
                                    .temperature(settings.temperature())
                                    .thinkingBudget(settings.thinkingBudget())
                                    .labels(Map.of("prompt", PROMPT_ID)))
                            .system(system -> system.text(systemPrompt)
                                    .param("firm", facts.firm())
                                    .param("position", facts.position()))
                            .messages(conversation(history, question))
                            .tools(searchTools, namedCompanyTools, sectorTools, proposalTools)
                            .toolContext(context.asMap())
                            .stream()
                            .content())
                    .retryWhen(Retry.backoff(1, RETRY_AFTER)
                            .filter(failed -> answer.isEmpty() && context.recorder().steps().isEmpty()))
                    .doOnNext(chunk -> {
                        answer.append(chunk);
                        onAnswer.accept(chunk);
                    })
                    .blockLast();
        } catch (RuntimeException failed) {
            log.warn("Assistant model call failed", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        return answer.toString().strip();
    }

    private List<Message> conversation(List<AssistantTurn> history, String question) {
        List<AssistantTurn> recent = history.subList(
                Math.max(0, history.size() - settings.historyWindow()), history.size());
        List<Message> messages = new ArrayList<>(recent.size() * 2 + 1);
        for (AssistantTurn turn : recent) {
            messages.add(new UserMessage(turn.getQuestion()));
            messages.add(new AssistantMessage(turn.getAnswer()));
        }
        messages.add(new UserMessage(question));
        return messages;
    }
}
