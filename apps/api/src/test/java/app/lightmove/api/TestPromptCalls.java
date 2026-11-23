package app.lightmove.api;

import app.lightmove.api.core.llm.model.PromptCallCount;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.PromptCallCountWriter;
import app.lightmove.api.core.llm.service.PromptCallCounter;
import app.lightmove.api.core.llm.service.PromptCalls;
import app.lightmove.api.core.llm.service.PromptTrialRunner;
import app.lightmove.api.core.llm.service.VertexGeminiChatModelProvider;

/** {@link PromptCalls} for the unit tests that build a model-calling service by hand. */
public final class TestPromptCalls {

    private TestPromptCalls() {
    }

    /** The bundled files and nothing else: no registry, so no call carries a version and nothing is counted. */
    public static PromptCalls bundledOnly() {
        return bundledUnless(new PromptTrialRunner(null));
    }

    /** The bundled files, except while {@code trials} is running a version of its own: an offline eval's way in. */
    public static PromptCalls bundledUnless(PromptTrialRunner trials) {
        BundledPrompts bundled = new BundledPrompts();
        return new PromptCalls((promptId, kind) -> ServedPrompt.bundled(promptId, bundled.bodyOf(promptId)),
                new VertexGeminiChatModelProvider(null, null, null), new PromptCallCounter(new UncountedCalls()),
                trials);
    }

    private static final class UncountedCalls extends PromptCallCountWriter {

        private UncountedCalls() {
            super(null);
        }

        @Override
        public void add(PromptCallCount count) {
        }
    }
}
