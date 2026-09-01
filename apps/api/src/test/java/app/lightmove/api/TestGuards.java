package app.lightmove.api;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.llm.service.LlmGuards;
import java.util.List;

/**
 * {@link LlmGuards} wired the way the shipped configuration wires it, for the unit tests that build a
 * model-calling service by hand. Real phrases and a real repair budget — a stub here would test the
 * stub.
 */
public final class TestGuards {

    private TestGuards() {
    }

    public static LlmGuards guards() {
        return new LlmGuards(new LightMoveProperties(null, null, null, null, null,
                new LlmSettings(new LlmRateLimitSettings(true, 10, 20), 20_000, 1,
                        List.of("ignore previous instructions", "system prompt", "you are now")),
                null, null));
    }
}
