package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.BundledPrompt;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.RecordedBundledPrompt;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.reload.PlatformConfigListener;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Serves each prompt from the registry, held in memory until a platform change drops it. Only a version a person
 * chose overrides this build's own file: the production label is shared by every revision running, so a shipped
 * text a release's sync moved to is served from the build and merely numbered by the registry. Never the reason a call fails: with
 * the registry off or unreadable the bundled file serves, and a version whose model is retired, no longer listed
 * or unable to answer its kind of prompt is answered by the configured model.
 */
@Slf4j
@Component
public class RegistryPromptSource implements PromptSource, PlatformConfigListener {

    public static final String SCOPE = "prompts";

    private static final Duration RETRY_AFTER_FAILED_READ = Duration.ofSeconds(30);

    private final boolean enabled;
    private final BundledPrompts bundled;
    private final PromptRegistryStore store;
    private final AiModelCatalog models;
    private final Clock clock;

    private final Set<String> reportedModelFallbacks = ConcurrentHashMap.newKeySet();
    private final Object cacheLock = new Object();
    private volatile Map<String, ServedPrompt> serving;
    private volatile long retryReadAtMillis;
    private long reloads;

    public RegistryPromptSource(LightMoveProperties properties, BundledPrompts bundled, PromptRegistryStore store,
                                AiModelCatalog models, Clock clock) {
        this.enabled = properties.ai().promptRegistry().enabled();
        this.bundled = bundled;
        this.store = store;
        this.models = models;
        this.clock = clock;
    }

    @Override
    public ServedPrompt current(String promptId, PromptKind kind) {
        if (!enabled) {
            return bundledFile(promptId);
        }
        ServedPrompt served = serving().get(promptId);
        return served == null ? bundledFile(promptId) : onAUsableModel(served, kind);
    }

    @Override
    public String scope() {
        return SCOPE;
    }

    @Override
    public void reload() {
        synchronized (cacheLock) {
            reloads++;
            serving = null;
            retryReadAtMillis = 0;
        }
        reportedModelFallbacks.clear();
    }

    private Map<String, ServedPrompt> serving() {
        Map<String, ServedPrompt> held = serving;
        if (held != null) {
            return held;
        }
        if (clock.millis() < retryReadAtMillis) {
            return Map.of();
        }
        long reloadsBeforeTheRead;
        synchronized (cacheLock) {
            reloadsBeforeTheRead = reloads;
        }
        try {
            held = readRegistry();
        } catch (RuntimeException unreadable) {
            retryReadAtMillis = clock.millis() + RETRY_AFTER_FAILED_READ.toMillis();
            log.error("The prompt registry could not be read; serving the bundled prompts and retrying in {}s",
                    RETRY_AFTER_FAILED_READ.toSeconds(), unreadable);
            return Map.of();
        }
        // A reload during the read means this map may predate the change: kept, it would stay until the next bump,
        // since the backstop check has already seen the new number. Answer this call with it and read again next.
        synchronized (cacheLock) {
            if (reloads == reloadsBeforeTheRead) {
                serving = held;
            }
        }
        return held;
    }

    private Map<String, ServedPrompt> readRegistry() {
        Map<String, Integer> recorded = store.recordedBundledPrompts().stream()
                .collect(Collectors.toMap(RegistryPromptSource::keyOf, RecordedBundledPrompt::version));
        Map<String, ServedPrompt> read = new HashMap<>();
        for (BundledPrompt own : bundled.all()) {
            // No version until this build's sync has recorded the text: served all the same, and not counted.
            read.put(own.promptId(), new ServedPrompt(own.promptId(), own.body(),
                    recorded.get(keyOf(own.promptId(), own.sha256())), null, null, GenerationSettings.UNSET));
        }
        store.overridingProductionVersions().forEach(chosen -> read.put(chosen.promptId(), chosen));
        return Map.copyOf(read);
    }

    private ServedPrompt bundledFile(String promptId) {
        return ServedPrompt.bundled(promptId, bundled.bodyOf(promptId));
    }

    private ServedPrompt onAUsableModel(ServedPrompt served, PromptKind kind) {
        if (served.modelId() == null) {
            return served;
        }
        Optional<String> unusable = whyUnusable(models.find(served.provider(), served.modelId()), kind);
        if (unusable.isEmpty()) {
            return served;
        }
        if (reportedModelFallbacks.add(served.promptId() + ":" + served.version())) {
            log.warn("Prompt {} version {} names model {}, which is {}; the configured model answers instead",
                    served.promptId(), served.version(), served.modelId(), unusable.get());
        }
        return served.onTheConfiguredModel();
    }

    private Optional<String> whyUnusable(Optional<AiModel> model, PromptKind kind) {
        if (model.isEmpty()) {
            return Optional.of("not in the model catalogue");
        }
        if (model.get().isRetiredOn(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) {
            return Optional.of("past its retirement date");
        }
        ModelCapability needed = kind.needs();
        if (needed != null && !model.get().can(needed)) {
            return Optional.of("not able to answer a " + kind + " prompt");
        }
        return Optional.empty();
    }

    private static String keyOf(RecordedBundledPrompt recorded) {
        return keyOf(recorded.promptId(), recorded.sha256());
    }

    private static String keyOf(String promptId, String sha256) {
        return promptId + ":" + sha256;
    }
}
