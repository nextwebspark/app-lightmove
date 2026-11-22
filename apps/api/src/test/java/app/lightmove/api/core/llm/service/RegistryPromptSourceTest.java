package app.lightmove.api.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.AiSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PromptRegistrySettings;
import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.ServedPrompt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistryPromptSourceTest {

    private static final String PROMPT = "recruiter-shortlist";
    private static final String SHIPPED = new BundledPrompts().bodyOf(PROMPT);
    private static final Clock IN_2026 = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("off, the bundled file serves and the registry is never read")
    void offServesTheBundledFile() {
        AtomicInteger reads = new AtomicInteger();
        RegistryPromptSource source = source(false, IN_2026, () -> {
            reads.incrementAndGet();
            return List.of(edited("gemini-3.8-flash"));
        });

        ServedPrompt served = source.current(PROMPT);

        assertThat(served.body()).isEqualTo(SHIPPED);
        assertThat(served.version()).isNull();
        assertThat(reads).hasValue(0);
    }

    @Test
    @DisplayName("on, the production version serves with its model and settings, read once until a reload")
    void onServesTheProductionVersion() {
        AtomicInteger reads = new AtomicInteger();
        RegistryPromptSource source = source(true, IN_2026, () -> {
            reads.incrementAndGet();
            return List.of(edited("gemini-3.8-flash"));
        });

        ServedPrompt served = source.current(PROMPT);
        source.current(PROMPT);

        assertThat(served.body()).isEqualTo("An edited prompt.");
        assertThat(served.version()).isEqualTo(4);
        assertThat(served.modelId()).isEqualTo("gemini-3.8-flash");
        assertThat(served.settings().temperature()).isEqualTo(0.3);
        assertThat(reads).hasValue(1);

        source.reload();
        source.current(PROMPT);
        assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("a registry that cannot be read serves the bundled file, and is not asked again on every call")
    void anUnreadableRegistryServesTheBundledFile() {
        AtomicInteger reads = new AtomicInteger();
        RegistryPromptSource source = source(true, IN_2026, () -> {
            reads.incrementAndGet();
            throw new IllegalStateException("database is down");
        });

        ServedPrompt served = source.current(PROMPT);
        source.current(PROMPT);

        assertThat(served.body()).isEqualTo(SHIPPED);
        assertThat(served.version()).isNull();
        assertThat(reads).hasValue(1);
    }

    @Test
    @DisplayName("a prompt the registry holds no version of is served from its bundled file")
    void aPromptWithNoVersionServesTheBundledFile() {
        RegistryPromptSource source = source(true, IN_2026, List::of);

        assertThat(source.current(PROMPT).body()).isEqualTo(SHIPPED);
    }

    @Test
    @DisplayName("a version on a model past its retirement date is answered by the configured model")
    void aRetiredModelFallsBackToTheConfiguredOne() {
        Clock afterRetirement = Clock.fixed(Instant.parse("2027-09-02T00:00:00Z"), ZoneOffset.UTC);
        RegistryPromptSource source = source(true, afterRetirement, () -> List.of(edited("gemini-3.8-flash")));

        ServedPrompt served = source.current(PROMPT);

        assertThat(served.modelId()).isNull();
        assertThat(served.body()).isEqualTo("An edited prompt.");
        assertThat(served.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("a version on a model the catalogue no longer lists is answered by the configured model")
    void anUnlistedModelFallsBackToTheConfiguredOne() {
        RegistryPromptSource source = source(true, IN_2026, () -> List.of(edited("gemini-1.0-retired")));

        assertThat(source.current(PROMPT).modelId()).isNull();
    }

    private static ServedPrompt edited(String modelId) {
        return new ServedPrompt(PROMPT, "An edited prompt.", 4, AiProvider.VERTEX_GEMINI, modelId,
                new GenerationSettings(0.3, null, null));
    }

    private static RegistryPromptSource source(boolean enabled, Clock clock, Supplier<List<ServedPrompt>> registry) {
        PromptRegistryStore store = new PromptRegistryStore(null, null) {
            @Override
            public List<ServedPrompt> productionVersions() {
                return registry.get();
            }
        };
        LightMoveProperties properties = new LightMoveProperties(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                new AiSettings(new PromptRegistrySettings(enabled, Duration.ofSeconds(30))));
        return new RegistryPromptSource(properties, new BundledPrompts(), store, new AiModelCatalog(), clock);
    }
}
