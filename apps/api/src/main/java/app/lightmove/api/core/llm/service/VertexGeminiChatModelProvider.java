package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
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

    private final String configuredModel;
    private final GenerationSettings configuredSettings;

    public VertexGeminiChatModelProvider(
            @Value("${spring.ai.google.genai.chat.model:}") String chatModel,
            @Value("${spring.ai.google.genai.chat.temperature:#{null}}") Double chatTemperature,
            @Value("${spring.ai.google.genai.chat.max-output-tokens:#{null}}") Integer chatMaxOutputTokens) {
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
            options.thinkingBudget(settings.thinkingBudget());
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

    private static Map<String, String> labelsOf(PromptCallOptions call) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(PROMPT_LABEL, call.callLabel());
        if (call.promptVersion() != null) {
            labels.put(PROMPT_VERSION_LABEL, call.promptVersion().toString());
        }
        return labels;
    }
}
