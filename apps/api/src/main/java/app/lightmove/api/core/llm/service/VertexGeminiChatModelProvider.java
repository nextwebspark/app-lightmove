package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.constant.ThinkingLevel;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gemini on Vertex AI. Only what a call names is set: everything else is inherited from
 * {@code spring.ai.google.genai.chat.*}, which a call's options are merged over.
 */
@Component
public class VertexGeminiChatModelProvider implements ChatModelProvider {

    /**
     * Asked for natively, so there is no markdown fence on the answer. Without it Gemini wrapped its
     * reply in a {@code ```json} fence, {@code StructuredOutputValidationAdvisor} handed that text to
     * Jackson verbatim, and every call failed validation on the leading backtick — then spent its
     * repair attempt re-asking a question whose answer came back fenced identically.
     */
    private static final String ANSWER_MIME_TYPE = "application/json";

    static final String PROMPT_LABEL = "prompt";

    /** Snake case: Google Cloud refuses a label key with an upper-case letter. */
    static final String PROMPT_VERSION_LABEL = "prompt_version";

    /** A budget up to this reads as LOW, up to {@link #MEDIUM_UP_TO} as MEDIUM, and past it as HIGH. */
    private static final int LOW_UP_TO = 1024;
    private static final int MEDIUM_UP_TO = 8192;

    /** The model generations that still reason by budget; any other id reasons by level. */
    private static final List<String> BUDGET_GENERATION_PREFIXES = List.of("gemini-1.", "gemini-2.");

    /**
     * What an id the catalogue does not list is assumed to offer: every Gemini 3 and later model takes these, and
     * some refuse MINIMAL, so a {@code LLM_CHAT_MODEL} pointed at a new id never gets the budget Gemini 4 refuses.
     */
    private static final List<ThinkingLevel> LEVELS_OF_AN_UNLISTED_MODEL =
            List.of(ThinkingLevel.LOW, ThinkingLevel.MEDIUM, ThinkingLevel.HIGH);

    private final AiModelCatalog models;
    private final String configuredModel;
    private final GenerationSettings configuredSettings;

    public VertexGeminiChatModelProvider(
            AiModelCatalog models,
            @Value("${spring.ai.google.genai.chat.model:}") String chatModel,
            @Value("${spring.ai.google.genai.chat.temperature:#{null}}") Double chatTemperature,
            @Value("${spring.ai.google.genai.chat.max-output-tokens:#{null}}") Integer chatMaxOutputTokens) {
        this.models = models;
        this.configuredModel = chatModel == null || chatModel.isBlank() ? null : chatModel;
        this.configuredSettings = new GenerationSettings(chatTemperature, null, chatMaxOutputTokens);
    }

    @Override
    public AiProvider provider() {
        return AiProvider.VERTEX_GEMINI;
    }

    @Override
    public String configuredModel() {
        return configuredModel;
    }

    @Override
    public GenerationSettings configuredSettings() {
        return configuredSettings;
    }

    @Override
    public ChatOptions.Builder<?> optionsFor(PromptCallOptions call) {
        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder();
        if (call.model() != null) {
            options.model(call.model());
        }
        GenerationSettings settings = call.settings();
        if (settings.temperature() != null) {
            options.temperature(settings.temperature());
        }
        if (settings.thinkingBudget() != null) {
            List<ThinkingLevel> levels = thinkingLevelsOf(call.model() != null ? call.model() : configuredModel);
            if (levels.isEmpty()) {
                options.thinkingBudget(settings.thinkingBudget());
            } else {
                options.thinkingLevel(toGoogle(levelFor(settings.thinkingBudget(), levels)));
            }
        }
        if (settings.maxOutputTokens() != null) {
            options.maxOutputTokens(settings.maxOutputTokens());
        }
        if (call.kind() == PromptKind.STRUCTURED) {
            options.responseMimeType(ANSWER_MIME_TYPE);
        }
        options.labels(labelsOf(call));
        return options;
    }

    /** Empty for a model that still takes a budget: Gemini 2.5 and earlier, or no model named at all. */
    private List<ThinkingLevel> thinkingLevelsOf(String modelId) {
        if (modelId == null) {
            return List.of();
        }
        return models.find(AiProvider.VERTEX_GEMINI, modelId).map(AiModel::thinkingLevels)
                .orElseGet(() -> BUDGET_GENERATION_PREFIXES.stream().anyMatch(modelId::startsWith)
                        ? List.of() : LEVELS_OF_AN_UNLISTED_MODEL);
    }

    /**
     * Gemini 3 and later reason by level: a thinking budget is deprecated there, intermittently refused with a 400,
     * and always refused by Gemini 4, so the budget a version or a feature sets is read as the nearest level the
     * model offers. 0, "no thinking", is the least it offers, since none of them can switch thinking off.
     */
    static ThinkingLevel levelFor(int budget, List<ThinkingLevel> offered) {
        ThinkingLevel wanted = budget == 0 ? ThinkingLevel.MINIMAL
                : budget <= LOW_UP_TO ? ThinkingLevel.LOW
                : budget <= MEDIUM_UP_TO ? ThinkingLevel.MEDIUM
                : ThinkingLevel.HIGH;
        return offered.stream().filter(level -> level.compareTo(wanted) >= 0).findFirst()
                .orElse(offered.getLast());
    }

    private static GoogleGenAiThinkingLevel toGoogle(ThinkingLevel level) {
        return switch (level) {
            case MINIMAL -> GoogleGenAiThinkingLevel.MINIMAL;
            case LOW -> GoogleGenAiThinkingLevel.LOW;
            case MEDIUM -> GoogleGenAiThinkingLevel.MEDIUM;
            case HIGH -> GoogleGenAiThinkingLevel.HIGH;
        };
    }

    private static Map<String, String> labelsOf(PromptCallOptions call) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(PROMPT_LABEL, call.callLabel());
        if (call.promptVersion() != null) {
            labels.put(PROMPT_VERSION_LABEL, call.promptVersion().toString());
        }
        return labels;
    }
}
