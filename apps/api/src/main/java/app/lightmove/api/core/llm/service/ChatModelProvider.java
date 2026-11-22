package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Everything one model provider needs said its own way: the answer format, the thinking budget, the billing labels.
 * The callers name no vendor class, so a second provider is a second implementation and a catalogue entry.
 */
public interface ChatModelProvider {

    AiProvider provider();

    /** The deployment's chat model, which a call naming none is answered by; null when the provider's own. */
    String configuredModel();

    /** The deployment's settings a call's options are merged over; a null field is the provider's own default. */
    GenerationSettings configuredSettings();

    ChatOptions.Builder<?> optionsFor(PromptCallOptions call);
}
