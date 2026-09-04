package app.lightmove.api;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import java.util.List;

/**
 * {@link LlmCallPolicy} wired the way the shipped configuration wires it, for the unit tests that
 * build a model-calling service by hand. Real phrases and a real repair budget — a stub here would
 * test the stub.
 */
public final class TestLlmCallPolicy {

    private TestLlmCallPolicy() {
    }

    public static LlmCallPolicy asShipped() {
        return new LlmCallPolicy(new LightMoveProperties(null, null, null, null, null,
                new LlmSettings(new LlmRateLimitSettings(true, 10, 20), 20_000, 1,
                        List.of("ignore previous instructions", "system prompt", "you are now")),
                null, null));
    }
}
