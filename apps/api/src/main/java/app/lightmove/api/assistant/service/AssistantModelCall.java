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
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * The assistant's two kinds of model call: a specialist with its own tools, and the supervisor whose
 * tools are specialists. Both send the chat's recent turns, and both hand the tools only the server's
 * {@link AssistantToolContext} — never a key Spring AI added to an outer call's context.
 *
 * <p>Deliberately not {@code LlmCallPolicy.forPrompt}: its SafeGuardAdvisor phrase list would refuse
 * ordinary conversation ("ignore the declined ones"); #429 owns the replacement. The ChatCallLog
 * attribution is kept, since that keeps prompt and answer content out of the logs.
 */
@Service
public class AssistantModelCall {

    static final String SUPERVISOR_PROMPT_ID = "assistant-supervisor";

    private final ChatClient chatClient;
    private final AssistantSettings settings;
    private final Resource supervisorPrompt;

    public AssistantModelCall(ChatClient chatClient, LightMoveProperties properties,
                              @Value("classpath:prompts/assistant-supervisor.st") Resource supervisorPrompt) {
        this.chatClient = chatClient;
        this.settings = properties.assistant();
        this.supervisorPrompt = supervisorPrompt;
    }

    public String askSpecialist(AssistantSpecialist specialist, List<AssistantTurn> history, String question,
                                AssistantToolContext context) {
        String answer = chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, specialist.promptId()))
                .options(options(specialist.promptId(), settings.thinkingBudget()))
                .system(system -> system.text(specialist.systemPrompt()).params(specialist.systemParams(context)))
                .messages(conversation(history, question))
                .tools(specialist.tools().toArray())
                .toolContext(context.asMap())
                .call()
                .content();
        return answerOf(answer);
    }

    /** No thinking budget: the supervisor only chooses whom to ask and passes the answer on. */
    public String askSupervisor(List<SpecialistToolCallback> specialists, List<AssistantTurn> history,
                                String question, AssistantToolContext context) {
        String answer = chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatCallLog.PROMPT_ID_ATTRIBUTE, SUPERVISOR_PROMPT_ID))
                .options(options(SUPERVISOR_PROMPT_ID, 0))
                .system(supervisorPrompt)
                .messages(conversation(history, question))
                .tools(specialists.toArray())
                .toolContext(context.asMap())
                .call()
                .content();
        return answerOf(answer);
    }

    /** Every call is sent {@link #conversation}'s card blocks, so any answer may echo one back. */
    private static String answerOf(String answer) {
        return answer == null ? "" : CardMemory.stripFrom(answer);
    }

    private GoogleGenAiChatOptions.Builder options(String promptId, int thinkingBudget) {
        return GoogleGenAiChatOptions.builder()
                .model(settings.model())
                .temperature(settings.temperature())
                .thinkingBudget(thinkingBudget)
                .labels(Map.of("prompt", promptId));
    }

    /**
     * Each earlier answer carries the card it showed, which the answer's own text never lists — the
     * newest {@link CardMemory#CARDS_LISTED_IN_FULL} row by row, older ones as a title and a count.
     */
    static List<Message> conversation(List<AssistantTurn> history, String question) {
        List<Message> messages = new ArrayList<>(history.size() * 2 + 1);
        int cardsLeft = (int) history.stream().filter(AssistantModelCall::hasCard).count();
        for (AssistantTurn turn : history) {
            messages.add(new UserMessage(turn.getQuestion()));
            if (!hasCard(turn)) {
                messages.add(new AssistantMessage(turn.getAnswer()));
                continue;
            }
            boolean listed = cardsLeft-- <= CardMemory.CARDS_LISTED_IN_FULL;
            messages.add(new AssistantMessage(turn.getAnswer() + "\n\n"
                    + CardMemory.render(turn.getProposal(), turn.getProposalAccepted(), listed)));
        }
        messages.add(new UserMessage(question));
        return messages;
    }

    private static boolean hasCard(AssistantTurn turn) {
        return turn.getProposal() != null && !turn.getProposal().companies().isEmpty();
    }
}
