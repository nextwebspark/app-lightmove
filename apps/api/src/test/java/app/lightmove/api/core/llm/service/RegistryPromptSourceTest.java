package app.lightmove.api.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.AiSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PromptRegistrySettings;
import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.BundledPrompt;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.RecordedBundledPrompt;
import app.lightmove.api.core.llm.model.ServedPrompt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

        ServedPrompt served = source.current(PROMPT, PromptKind.PROSE);

        assertThat(served.body()).isEqualTo(SHIPPED);
        assertThat(served.version()).isNull();
        assertThat(reads).hasValue(0);
    }

    @Test
    @DisplayName("on, an edited production version serves with its model and settings, read once until a reload")
    void onServesTheEditedProductionVersion() {
        AtomicInteger reads = new AtomicInteger();
        RegistryPromptSource source = source(true, IN_2026, () -> {
            reads.incrementAndGet();
            return List.of(edited("gemini-3.8-flash"));
        });

        ServedPrompt served = source.current(PROMPT, PromptKind.PROSE);
        source.current(PROMPT, PromptKind.PROSE);

        assertThat(served.body()).isEqualTo("An edited prompt.");
        assertThat(served.version()).isEqualTo(4);
        assertThat(served.modelId()).isEqualTo("gemini-3.8-flash");
        assertThat(served.settings().temperature()).isEqualTo(0.3);
        assertThat(reads).hasValue(1);

        source.reload();
        source.current(PROMPT, PromptKind.PROSE);
        assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("two revisions sharing one registry each serve their own shipped text, under their own version")
    void eachRevisionServesItsOwnShippedText() {
        BundledPrompt older = shippedAs("The older revision's text.");
        BundledPrompt newer = shippedAs("The newer revision's text.");
        List<RecordedBundledPrompt> recorded = List.of(
                new RecordedBundledPrompt(PROMPT, older.sha256(), 1),
                new RecordedBundledPrompt(PROMPT, newer.sha256(), 2));

        ServedPrompt onTheOlder = source(shipping(older), new AiModelCatalog(), List::of, recorded)
                .current(PROMPT, PromptKind.PROSE);
        ServedPrompt onTheNewer = source(shipping(newer), new AiModelCatalog(), List::of, recorded)
                .current(PROMPT, PromptKind.PROSE);

        assertThat(onTheOlder.body()).isEqualTo("The older revision's text.");
        assertThat(onTheOlder.version()).isEqualTo(1);
        assertThat(onTheNewer.body()).isEqualTo("The newer revision's text.");
        assertThat(onTheNewer.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("a shipped text the registry has not recorded yet is served from the build, with no version")
    void aTextNotYetRecordedIsServedWithoutAVersion() {
        RegistryPromptSource source = source(true, IN_2026, List::of);

        ServedPrompt served = source.current(PROMPT, PromptKind.PROSE);

        assertThat(served.body()).isEqualTo(SHIPPED);
        assertThat(served.version()).isNull();
    }

    @Test
    @DisplayName("a registry that cannot be read serves the bundled file, and is not asked again on every call")
    void anUnreadableRegistryServesTheBundledFile() {
        AtomicInteger reads = new AtomicInteger();
        RegistryPromptSource source = source(true, IN_2026, () -> {
            reads.incrementAndGet();
            throw new IllegalStateException("database is down");
        });

        ServedPrompt served = source.current(PROMPT, PromptKind.PROSE);
        source.current(PROMPT, PromptKind.PROSE);

        assertThat(served.body()).isEqualTo(SHIPPED);
        assertThat(served.version()).isNull();
        assertThat(reads).hasValue(1);
    }

    @Test
    @DisplayName("a read that a reload overtook is not kept: the next call reads the registry again")
    void aReadOvertakenByAReloadIsNotKept() {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<RegistryPromptSource> reloading = new AtomicReference<>();
        RegistryPromptSource source = source(true, IN_2026, () -> {
            if (reads.incrementAndGet() == 1) {
                reloading.get().reload();
                return List.of();
            }
            return List.of(edited("gemini-3.8-flash"));
        });
        reloading.set(source);

        source.current(PROMPT, PromptKind.PROSE);

        assertThat(source.current(PROMPT, PromptKind.PROSE).body()).isEqualTo("An edited prompt.");
        assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("a version on a model past its retirement date is answered by the configured model")
    void aRetiredModelFallsBackToTheConfiguredOne() {
        Clock afterRetirement = Clock.fixed(Instant.parse("2027-09-02T00:00:00Z"), ZoneOffset.UTC);
        RegistryPromptSource source = source(true, afterRetirement, () -> List.of(edited("gemini-3.8-flash")));

        ServedPrompt served = source.current(PROMPT, PromptKind.PROSE);

        assertThat(served.modelId()).isNull();
        assertThat(served.body()).isEqualTo("An edited prompt.");
        assertThat(served.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("a version on a model the catalogue no longer lists is answered by the configured model")
    void anUnlistedModelFallsBackToTheConfiguredOne() {
        RegistryPromptSource source = source(true, IN_2026, () -> List.of(edited("gemini-1.0-retired")));

        assertThat(source.current(PROMPT, PromptKind.PROSE).modelId()).isNull();
    }

    @Test
    @DisplayName("a version on a model that cannot call tools answers a tool-calling prompt on the configured model")
    void aModelUnableToAnswerThePromptsKindFallsBack() {
        AiModelCatalog answersOnly = new AiModelCatalog() {
            @Override
            public Optional<AiModel> find(AiProvider provider, String modelId) {
                return super.find(provider, modelId).map(model -> new AiModel(model.provider(), model.modelId(),
                        model.displayName(), model.retiresOn(), model.dataTerms(),
                        Set.of(ModelCapability.STRUCTURED_ANSWER), model.temperature(), model.thinkingBudget(),
                        model.maxOutputTokens()));
            }
        };
        RegistryPromptSource source = source(new BundledPrompts(), answersOnly,
                () -> List.of(edited("gemini-3.8-flash")), List.of());

        assertThat(source.current(PROMPT, PromptKind.TOOL_CALLING).modelId()).isNull();
        assertThat(source.current(PROMPT, PromptKind.STRUCTURED).modelId()).isEqualTo("gemini-3.8-flash");
        assertThat(source.current(PROMPT, PromptKind.PROSE).modelId()).isEqualTo("gemini-3.8-flash");
    }

    private static ServedPrompt edited(String modelId) {
        return new ServedPrompt(PROMPT, "An edited prompt.", 4, AiProvider.VERTEX_GEMINI, modelId,
                new GenerationSettings(0.3, null, null));
    }

    private static BundledPrompt shippedAs(String body) {
        return new BundledPrompt(PROMPT, body, BundledPrompts.sha256Of(body));
    }

    private static BundledPrompts shipping(BundledPrompt prompt) {
        return new BundledPrompts() {
            @Override
            public List<BundledPrompt> all() {
                return List.of(prompt);
            }
        };
    }

    private static RegistryPromptSource source(boolean enabled, Clock clock, Supplier<List<ServedPrompt>> edited) {
        return new RegistryPromptSource(properties(enabled), new BundledPrompts(), store(edited, List.of()),
                new AiModelCatalog(), clock);
    }

    private static RegistryPromptSource source(BundledPrompts bundled, AiModelCatalog models,
                                               Supplier<List<ServedPrompt>> edited,
                                               List<RecordedBundledPrompt> recorded) {
        return new RegistryPromptSource(properties(true), bundled, store(edited, recorded), models, IN_2026);
    }

    private static PromptRegistryStore store(Supplier<List<ServedPrompt>> edited,
                                             List<RecordedBundledPrompt> recorded) {
        return new PromptRegistryStore(null, null) {
            @Override
            public List<ServedPrompt> overridingProductionVersions() {
                return edited.get();
            }

            @Override
            public List<RecordedBundledPrompt> recordedBundledPrompts() {
                return recorded;
            }
        };
    }

    private static LightMoveProperties properties(boolean enabled) {
        return new LightMoveProperties(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                new AiSettings(new PromptRegistrySettings(enabled)));
    }
}
