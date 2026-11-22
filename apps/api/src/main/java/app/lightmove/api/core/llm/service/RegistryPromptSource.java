package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.reload.PlatformConfigListener;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Serves each prompt's production version from the registry, held in memory until a platform change drops it.
 * Never the reason a call fails: with the registry off, unreadable, or holding no version of a prompt, the bundled
 * file serves; a version whose model is retired or no longer listed is answered by the configured model.
 */
@Slf4j
@Primary
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
    private volatile Map<String, ServedPrompt> production;
    private volatile long retryReadAtMillis;

    public RegistryPromptSource(LightMoveProperties properties, BundledPrompts bundled, PromptRegistryStore store,
                                AiModelCatalog models, Clock clock) {
        this.enabled = properties.ai().promptRegistry().enabled();
        this.bundled = bundled;
        this.store = store;
        this.models = models;
        this.clock = clock;
    }

    @Override
    public ServedPrompt current(String promptId) {
        if (!enabled) {
            return bundled.current(promptId);
        }
        ServedPrompt served = productionVersions().get(promptId);
        return served == null ? bundled.current(promptId) : onAUsableModel(served);
    }

    @Override
    public String scope() {
        return SCOPE;
    }

    @Override
    public void reload() {
        production = null;
        retryReadAtMillis = 0;
        reportedModelFallbacks.clear();
    }

    private Map<String, ServedPrompt> productionVersions() {
        Map<String, ServedPrompt> held = production;
        if (held != null) {
            return held;
        }
        if (clock.millis() < retryReadAtMillis) {
            return Map.of();
        }
        try {
            held = store.productionVersions().stream()
                    .collect(Collectors.toUnmodifiableMap(ServedPrompt::promptId, Function.identity()));
            production = held;
            return held;
        } catch (RuntimeException unreadable) {
            retryReadAtMillis = clock.millis() + RETRY_AFTER_FAILED_READ.toMillis();
            log.error("The prompt registry could not be read; serving the bundled prompts and retrying in {}s",
                    RETRY_AFTER_FAILED_READ.toSeconds(), unreadable);
            return Map.of();
        }
    }

    private ServedPrompt onAUsableModel(ServedPrompt served) {
        if (served.modelId() == null) {
            return served;
        }
        Optional<AiModel> model = models.find(served.provider(), served.modelId());
        if (model.isPresent() && !model.get().isRetiredOn(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) {
            return served;
        }
        if (reportedModelFallbacks.add(served.promptId() + ":" + served.version())) {
            log.warn("Prompt {} version {} names model {}, which is {}; the configured model answers instead",
                    served.promptId(), served.version(), served.modelId(),
                    model.isPresent() ? "past its retirement date" : "not in the model catalogue");
        }
        return served.onTheConfiguredModel();
    }
}
