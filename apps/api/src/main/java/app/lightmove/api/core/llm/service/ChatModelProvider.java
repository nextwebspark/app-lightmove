package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Everything one model provider needs said its own way: the answer format, the thinking budget, the billing labels.
 * The callers name no vendor class, so a second provider is a second implementation and a catalogue entry.
 */
public interface ChatModelProvider {

    AiProvider provider();

    ChatOptions.Builder<?> optionsFor(PromptCallOptions call);
}
