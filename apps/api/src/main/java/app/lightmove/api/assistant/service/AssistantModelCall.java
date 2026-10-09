package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.service.ChatCallLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * The assistant's one model call, run through Spring AI's tool loop. Deliberately not
 * {@code LlmCallPolicy.forPrompt}: its SafeGuardAdvisor would refuse "ignore the declined ones" (#429).
 */
@Service
public class AssistantModelCall {

    /** Kept from before playbooks existed, so the cost and log lines of an answer read as they did. */
    static final String PROMPT_ID = "assistant-turn";

    private final ChatClient chatClient;
    private final AssistantSettings settings;
    private final Resource corePrompt;

    public AssistantModelCall(ChatClient chatClient, LightMoveProperties properties,
                              @Value("classpath:prompts/assistant-core.st") Resource corePrompt) {
        this.chatClient = chatClient;
        this.settings = properties.assistant();
        this.corePrompt = corePrompt;
    }

    public String ask(Map<String, Object> systemParams, List<ToolCallback> tools, List<AssistantTurn> history,
                      String question, AssistantToolContext context) {
        ChatResponse response = chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, PROMPT_ID))
                .options(GoogleGenAiChatOptions.builder()
                        .model(settings.model())
                        .temperature(settings.temperature())
                        .thinkingBudget(settings.thinkingBudget())
                        .labels(Map.of("prompt", PROMPT_ID)))
                .system(system -> system.text(corePrompt).params(systemParams))
                .messages(conversation(history, question))
                .tools(tools.toArray())
                .toolContext(context.asMap())
                .call()
                .chatResponse();
        return answerOf(response, context);
    }

    /**
     * Every call is sent {@link #conversation}'s card and question blocks, so any answer may echo one back. The
     * tokens are Spring AI's sum over the call's tool rounds.
     */
    private String answerOf(ChatResponse response, AssistantToolContext context) {
        if (response == null) {
            return "";
        }
        Usage usage = response.getMetadata().getUsage();
        String model = response.getMetadata().getModel();
        context.recorder().spent(model == null || model.isBlank() ? settings.model() : model,
                usage.getPromptTokens(), usage.getCompletionTokens());
        Generation result = response.getResult();
        String answer = result == null ? null : result.getOutput().getText();
        return answer == null ? "" : QuestionMemory.stripFrom(CardMemory.stripFrom(answer));
    }

    /**
     * Each earlier answer carries the companies it suggested, which the answer's own text never lists —
     * the newest {@link CardMemory#CARDS_LISTED_IN_FULL} row by row, older ones as a title and a count —
     * or the questions it asked instead, which the consultant's next message answers and is sent with the
     * request they answer ({@link QuestionMemory#answering}).
     */
    static List<Message> conversation(List<AssistantTurn> history, String question) {
        List<Message> messages = new ArrayList<>(history.size() * 2 + 1);
        int cardsLeft = (int) history.stream().filter(AssistantModelCall::hasCard).count();
        for (int index = 0; index < history.size(); index++) {
            AssistantTurn turn = history.get(index);
            messages.add(new UserMessage(QuestionMemory.answering(history, index, turn.getQuestion())));
            if (!turn.getQuestions().isEmpty()) {
                messages.add(new AssistantMessage(turn.getAnswer() + "\n\n"
                        + QuestionMemory.render(turn.getQuestions())));
                continue;
            }
            if (!hasCard(turn)) {
                messages.add(new AssistantMessage(turn.getAnswer()));
                continue;
            }
            boolean listed = cardsLeft-- <= CardMemory.CARDS_LISTED_IN_FULL;
            messages.add(new AssistantMessage(turn.getAnswer() + "\n\n"
                    + CardMemory.render(turn.getProposal(), turn.getProposalAccepted(), listed)));
        }
        messages.add(new UserMessage(QuestionMemory.answering(history, history.size(), question)));
        return messages;
    }

    private static boolean hasCard(AssistantTurn turn) {
        return turn.getProposal() != null && !turn.getProposal().companies().isEmpty();
    }
}
