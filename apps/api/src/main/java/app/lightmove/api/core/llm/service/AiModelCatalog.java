package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.constant.ModelLaunchStage;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.constant.UnusableModelReason;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.SettingRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The models a prompt version may name, as provider + model id. GA models only, each with the earliest retirement
 * date its provider states: a version on a model past that date, or no longer listed here, is answered by the
 * deployment's configured model instead. Dates are Google's "model versions and lifecycle" page as read on
 * 2026-11-21; re-read it when adding or removing an entry.
 */
@Component
public class AiModelCatalog {

    static final String VERTEX_DATA_TERMS =
            "Vertex AI, global endpoint. Google Cloud's data processing terms, the same as today.";

    private static final Set<ModelCapability> ANSWERS_AND_TOOLS =
            Set.of(ModelCapability.STRUCTURED_ANSWER, ModelCapability.TOOL_CALLING);

    private static final SettingRange TEMPERATURE = new SettingRange(0, 1);
    private static final SettingRange THINKING_BUDGET = new SettingRange(0, 8192);
    private static final SettingRange MAX_OUTPUT_TOKENS = new SettingRange(256, 16384);

    private static final List<AiModel> MODELS = List.of(
            gemini("gemini-3.5-flash", "Gemini 3.5 Flash", LocalDate.of(2027, 5, 19)),
            gemini("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", LocalDate.of(2027, 7, 21)),
            gemini("gemini-3.8-flash", "Gemini 3.8 Flash", LocalDate.of(2027, 9, 2)),
            gemini("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", LocalDate.of(2027, 5, 7)),
            gemini("gemini-4-argon", "Gemini 4 Argon", LocalDate.of(2027, 10, 13)));

    public List<AiModel> all() {
        return MODELS;
    }

    public Optional<AiModel> find(AiProvider provider, String modelId) {
        return MODELS.stream()
                .filter(model -> model.provider() == provider && model.modelId().equals(modelId))
                .findFirst();
    }

    /**
     * Why a version naming this model cannot be answered by it on {@code day}, for a prompt of {@code kind}: the
     * configured model answers instead. Empty when the model will do. The one rule for saving a version, putting
     * one live and serving it, so the three cannot disagree.
     */
    public Optional<UnusableModelReason> unusableBecause(AiProvider provider, String modelId, PromptKind kind,
                                                         LocalDate day) {
        Optional<AiModel> model = provider == null || modelId == null ? Optional.empty() : find(provider, modelId);
        if (model.isEmpty()) {
            return Optional.of(UnusableModelReason.NOT_IN_CATALOGUE);
        }
        if (model.get().isRetiredOn(day)) {
            return Optional.of(UnusableModelReason.RETIRED);
        }
        ModelCapability needed = kind.needs();
        if (needed != null && !model.get().can(needed)) {
            return Optional.of(UnusableModelReason.LACKS_CAPABILITY);
        }
        return Optional.empty();
    }

    private static AiModel gemini(String modelId, String displayName, LocalDate retiresOn) {
        return new AiModel(AiProvider.VERTEX_GEMINI, modelId, displayName, ModelLaunchStage.GA, retiresOn,
                VERTEX_DATA_TERMS, ANSWERS_AND_TOOLS, TEMPERATURE, THINKING_BUDGET, MAX_OUTPUT_TOKENS);
    }
}
