package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.PreparedPrompt;
import app.lightmove.api.core.llm.model.PromptCallSpec;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

/**
 * One prompt whose answer binds to a record: its guard and schema advisors, and on every call the system text,
 * model and settings of the version serving now. Built once per caller by {@link StructuredPromptFactory}.
 */
public final class StructuredPrompt {

    private final ChatClient chatClient;
    private final PromptCalls prompts;
    private final PromptCallSpec spec;
    private final Consumer<ChatClient.AdvisorSpec> guarded;

    StructuredPrompt(ChatClient chatClient, PromptCalls prompts, PromptCallSpec spec,
                     Consumer<ChatClient.AdvisorSpec> guarded) {
        this.chatClient = chatClient;
        this.prompts = prompts;
        this.spec = spec;
        this.guarded = guarded;
    }

    /** The model's answer bound to {@code answerType}, or null when it answered nothing. */
    public <T> T ask(Class<T> answerType, Consumer<ChatClient.PromptUserSpec> user) {
        return ask(answerType, List.of(), user);
    }

    /**
     * The same, continuing a conversation: {@code history} is replayed between the system text and this
     * turn, because the model keeps nothing between calls. The guard reads every turn, not only the last.
     */
    public <T> T ask(Class<T> answerType, List<Message> history, Consumer<ChatClient.PromptUserSpec> user) {
        PreparedPrompt prompt = prompts.prepare(spec);
        return prompts.bound(chatClient.prompt()
                .advisors(guarded.andThen(prompt.attribution()))
                .options(prompt.options())
                .system(prompt.systemText())
                .messages(history)
                .user(user)
                .call()
                .entity(answerType));
    }
}
