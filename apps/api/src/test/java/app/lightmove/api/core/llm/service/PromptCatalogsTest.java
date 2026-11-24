package app.lightmove.api.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.BundledPrompt;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.PromptCallOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

/** The lists the registry keeps in code: the bundled prompts, the model catalogue, and the provider's options. */
class PromptCatalogsTest {

    private static final Path SHIPPED_PROMPTS = Path.of("src/main/resources/prompts");

    @Test
    @DisplayName("every shipped prompt file is a bundled prompt, served byte for byte")
    void everyShippedPromptIsBundled() throws IOException {
        BundledPrompts bundled = new BundledPrompts();
        List<String> ids = bundled.all().stream().map(BundledPrompt::promptId).toList();

        try (Stream<Path> files = Files.list(SHIPPED_PROMPTS)) {
            List<Path> shipped = files.filter(file -> file.getFileName().toString().endsWith(".st")).toList();
            assertThat(shipped).isNotEmpty();
            for (Path file : shipped) {
                String name = file.getFileName().toString();
                String id = name.endsWith("-system.st")
                        ? name.substring(0, name.length() - "-system.st".length())
                        : name.substring(0, name.length() - ".st".length());
                assertThat(ids).contains(id);
                assertThat(bundled.bodyOf(id)).isEqualTo(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    @DisplayName("every model states its retirement date, its data terms, what it can do and its settings' bounds")
    void everyModelIsFullyDescribed() {
        List<AiModel> models = new AiModelCatalog().all();

        assertThat(models).isNotEmpty();
        assertThat(models).extracting(AiModel::modelId).doesNotHaveDuplicates();
        for (AiModel model : models) {
            assertThat(model.provider()).as(model.modelId()).isNotNull();
            assertThat(model.displayName()).as(model.modelId()).isNotBlank();
            assertThat(model.retiresOn()).as("%s: the date its provider states", model.modelId()).isNotNull();
            assertThat(model.dataTerms()).as(model.modelId()).isNotBlank();
            assertThat(model.capabilities()).as(model.modelId()).isNotEmpty();
            assertThat(model.temperature()).as(model.modelId()).isNotNull();
            assertThat(model.thinkingBudget()).as(model.modelId()).isNotNull();
            assertThat(model.maxOutputTokens()).as(model.modelId()).isNotNull();
            assertThat(model.thinkingLevels()).as("%s: Gemini 3 and later reason by level", model.modelId())
                    .isNotEmpty().isSorted();
        }
    }

    @Test
    @DisplayName("a structured call asks for JSON and carries the prompt and its version as labels")
    void theVertexAdapterMapsACall() {
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) new VertexGeminiChatModelProvider(new AiModelCatalog(), null, null, null)
                .optionsFor(new PromptCallOptions("call-changes", 7, PromptKind.STRUCTURED, "gemini-3.8-flash",
                        new GenerationSettings(0.0, 0, 2048)))
                .build();

        assertThat(options.getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(options.getTemperature()).isEqualTo(0.0);
        assertThat(options.getThinkingBudget()).as("deprecated on Gemini 3, refused by Gemini 4").isNull();
        assertThat(options.getThinkingLevel()).as("3.8 Flash refuses MINIMAL").isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(options.getMaxOutputTokens()).isEqualTo(2048);
        assertThat(options.getResponseMimeType()).isEqualTo("application/json");
        assertThat(options.getLabels()).containsEntry("prompt", "call-changes").containsEntry("prompt_version", "7");
    }

    @Test
    @DisplayName("a thinking budget reaches a listed model as the nearest level it offers, and any other as a budget")
    void aBudgetIsReadAsALevelOnTheModelsThatTakeOne() {
        assertThat(thinkingOf("gemini-3.5-flash", 0).getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.MINIMAL);
        assertThat(thinkingOf("gemini-4-argon", 0).getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(thinkingOf("gemini-4-argon", 512).getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(thinkingOf("gemini-3.5-flash-lite", 4096).getThinkingLevel())
                .isEqualTo(GoogleGenAiThinkingLevel.MEDIUM);
        assertThat(thinkingOf("gemini-3.1-flash-lite", 20000).getThinkingLevel())
                .isEqualTo(GoogleGenAiThinkingLevel.HIGH);

        GoogleGenAiChatOptions unlisted = thinkingOf("gemini-2.5-flash", 0);
        assertThat(unlisted.getThinkingBudget()).isEqualTo(0);
        assertThat(unlisted.getThinkingLevel()).isNull();

        GoogleGenAiChatOptions configured = (GoogleGenAiChatOptions)
                new VertexGeminiChatModelProvider(new AiModelCatalog(), "gemini-3.5-flash-lite", null, null)
                        .optionsFor(new PromptCallOptions("assistant-core", null, PromptKind.TOOL_CALLING, null,
                                new GenerationSettings(null, 512, null)))
                        .build();
        assertThat(configured.getThinkingLevel()).as("the configured model when a call names none")
                .isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(configured.getThinkingBudget()).isNull();
    }

    private static GoogleGenAiChatOptions thinkingOf(String modelId, int budget) {
        return (GoogleGenAiChatOptions) new VertexGeminiChatModelProvider(new AiModelCatalog(), null, null, null)
                .optionsFor(new PromptCallOptions("call-changes", 1, PromptKind.STRUCTURED, modelId,
                        new GenerationSettings(0.0, budget, null)))
                .build();
    }

    @Test
    @DisplayName("a prose call from the bundled file asks for no answer format and carries no version label")
    void aProseCallFromTheBundledFileNamesOnlyItsPrompt() {
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) new VertexGeminiChatModelProvider(new AiModelCatalog(), null, null, null)
                .optionsFor(new PromptCallOptions("recruiter-shortlist", null, PromptKind.PROSE, null,
                        GenerationSettings.UNSET))
                .build();

        assertThat(options.getResponseMimeType()).isNull();
        assertThat(options.getLabels()).containsOnlyKeys("prompt");
    }
}
