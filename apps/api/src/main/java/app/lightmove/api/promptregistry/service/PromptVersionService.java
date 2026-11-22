package app.lightmove.api.promptregistry.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.SettingRange;
import app.lightmove.api.core.llm.service.AiModelCatalog;
import app.lightmove.api.core.llm.service.BundledPrompts;
import app.lightmove.api.core.llm.service.PromptRegistryStore;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import app.lightmove.api.promptregistry.dto.PromptDetail;
import app.lightmove.api.promptregistry.dto.SavePromptVersionRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a new version of a prompt: its text, model and settings. A save never changes what serves; putting a
 * version live is {@link PromptPromotionService}'s.
 */
@Service
@RequiredArgsConstructor
public class PromptVersionService {

    private final PromptCatalogService catalog;
    private final PromptRegistryStore store;
    private final BundledPrompts bundled;
    private final AiModelCatalog models;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    public PromptDetail save(UUID userId, String promptId, SavePromptVersionRequest request,
                             HttpServletRequest httpRequest) {
        PlatformPrompt prompt = catalog.require(promptId);
        GenerationSettings settings = request.settings() == null ? GenerationSettings.UNSET : request.settings();
        Optional<AiModel> named = namedModel(prompt, request.provider(), request.modelId());
        requireWithinBounds(settings, named.or(() -> configuredModel(prompt)).orElse(models.all().getFirst()));
        PromptContract.require(bundled.bodyOf(promptId), request.body());

        int version = transactions.execute(status -> {
            store.lockPrompt(promptId);
            return store.insertEdited(promptId, request.body(), named.map(AiModel::provider).orElse(null),
                    named.map(AiModel::modelId).orElse(null), settings, request.changeNote().strip(), userId);
        });
        audit.event(PlatformEventType.PROMPT_VERSION_SAVED).actor(userId)
                .target("prompt", promptId)
                .detail("version", version)
                .detailIfPresent("model", named.map(AiModel::modelId).orElse(null))
                .detail("changeNote", request.changeNote().strip())
                .from(httpRequest)
                .record();
        return catalog.get(promptId);
    }

    private Optional<AiModel> namedModel(PlatformPrompt prompt, AiProvider provider, String modelId) {
        if (provider == null && (modelId == null || modelId.isBlank())) {
            return Optional.empty();
        }
        AiModel model = provider == null || modelId == null ? null : models.find(provider, modelId).orElse(null);
        if (model == null) {
            throw unavailable("That model is not one a prompt can use.");
        }
        if (model.isRetiredOn(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) {
            throw unavailable("That model has been retired. Choose another.");
        }
        ModelCapability needed = prompt.getKind().needs();
        if (needed != null && !model.can(needed)) {
            throw unavailable(needed == ModelCapability.TOOL_CALLING
                    ? "This prompt calls tools, which that model cannot do. Choose another."
                    : "This prompt needs a structured answer, which that model cannot give. Choose another.");
        }
        return Optional.of(model);
    }

    private Optional<AiModel> configuredModel(PlatformPrompt prompt) {
        String configured = catalog.specOf(prompt).defaultModel();
        return configured == null ? Optional.empty() : models.find(AiProvider.VERTEX_GEMINI, configured);
    }

    private static void requireWithinBounds(GenerationSettings settings, AiModel model) {
        requireWithin("settings.temperature", settings.temperature(), model.temperature(), "Temperature");
        requireWithin("settings.thinkingBudget", settings.thinkingBudget(), model.thinkingBudget(), "Thinking budget");
        requireWithin("settings.maxOutputTokens", settings.maxOutputTokens(), model.maxOutputTokens(),
                "Max output tokens");
    }

    private static void requireWithin(String field, Number value, SettingRange range, String label) {
        if (value != null && !range.holds(value.doubleValue())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                    label + " must be between " + plain(range.min()) + " and " + plain(range.max()) + " on this model");
        }
    }

    private static String plain(double bound) {
        return bound == Math.rint(bound) ? String.valueOf((long) bound) : String.valueOf(bound);
    }

    private static ApiException unavailable(String message) {
        return ApiException.withField(ErrorCode.PROMPT_MODEL_UNAVAILABLE, "modelId", message);
    }
}
