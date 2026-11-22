package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.PreparedPrompt;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import app.lightmove.api.core.llm.model.PromptCallSpec;
import app.lightmove.api.core.llm.model.ServedPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * What every model call asks before it is made: the prompt version serving now, turned into the system text, the
 * provider's options and the wiring that counts the call. Asked per call, so a version put live serves at once.
 */
@Component
@RequiredArgsConstructor
public class PromptCalls {

    private final PromptSource prompts;
    private final ChatModelProvider provider;
    private final PromptCallCounter counter;

    public PreparedPrompt prepare(PromptCallSpec spec) {
        ServedPrompt served = prompts.current(spec.promptId(), spec.kind());
        String model = served.modelId() != null ? served.modelId() : spec.defaultModel();
        PromptCallOptions call = new PromptCallOptions(spec.callLabel(), served.version(), spec.kind(), model,
                served.settings().orElse(spec.defaults()));
        return new PreparedPrompt(served.body(), model, provider.optionsFor(call),
                counter.attributing(served, provider.provider()));
    }
}
