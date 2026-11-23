package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.PreparedPrompt;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import app.lightmove.api.core.llm.model.PromptCallSpec;
import app.lightmove.api.core.llm.model.PromptCallTrace;
import app.lightmove.api.core.llm.model.PromptVersionRef;
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
    private final PromptTrialRunner trials;

    public PreparedPrompt prepare(PromptCallSpec spec) {
        ServedPrompt underTrial = trials.versionUnderTrial(spec.promptId());
        ServedPrompt served = underTrial != null ? underTrial : prompts.current(spec.promptId(), spec.kind());
        String model = served.modelId() != null ? served.modelId() : spec.defaultModel();
        PromptCallOptions call = new PromptCallOptions(spec.callLabel(), served.version(), spec.kind(), model,
                served.settings().orElse(spec.defaults()));
        // A trial is a console experiment, not a workspace served: counted, it would put calls on a version never
        // live and mix experiments into the live version's error and token rates.
        if (underTrial != null) {
            return new PreparedPrompt(served.body(), model, provider.optionsFor(call), trials.recording(), null);
        }
        PromptCallTrace trace = served.version() == null ? null : new PromptCallTrace(
                new PromptVersionRef(served.promptId(), served.version()), provider.provider(),
                model != null ? model : provider.configuredModel(),
                // Only a structured prompt is re-asked; a tool loop's further rounds are ordinary calls.
                spec.kind() == PromptKind.STRUCTURED);
        return new PreparedPrompt(served.body(), model, provider.optionsFor(call), counter.attributing(trace), trace);
    }

    /** Hands a bound answer to a running trial, and back to the caller unchanged. */
    public <T> T bound(T answer) {
        trials.bound(answer);
        return answer;
    }

    /**
     * A call that threw after its model answered in full: the answer would not bind, which is counted against its
     * version. One call is one failure, so an answer cut short stays the failure its finish reason already counted.
     */
    void failedToBind(PreparedPrompt prompt) {
        PromptCallTrace trace = prompt.trace();
        if (trace != null && trace.wasAnswered() && !trace.wasCutShort()) {
            counter.countSchemaFailure(prompt.trace());
        }
    }
}
