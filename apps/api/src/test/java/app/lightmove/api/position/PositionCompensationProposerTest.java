package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ProposedCompensation;
import app.lightmove.api.position.service.ExtractedFieldReader;
import app.lightmove.api.position.service.PositionCompensationProposer;
import app.lightmove.api.position.service.PositionDocumentRedactor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * The model call behind step four's "Read from document" (#1049). Like step two's, nothing backs it up:
 * a failed call is {@link ExtractionSource#NONE}, and a figure the document does not state is never proposed.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PositionCompensationProposerTest extends FlowTestSupport {

    @Autowired PositionDocumentRedactor redactor;
    @Autowired ExtractedFieldReader fieldReader;

    /** The package section of the CHRO position specification #1049 was raised against. */
    private static final String DOCUMENT_TEXT = """
            Chief Human Resources Officer (CHRO)
            Confidential Client

            Package
            The CHRO will have the following compensation
            Base: AED90,000 – AED120,000
            Bonus: 25% of total fixed
            Allowances:
            Housing: 100,000 per year
            Transport: 400 per month
            """;

    private static final String CHRO_ANSWER = """
            {"currency":"AED","currencySnippet":"AED90,000",
             "salaryMin":"90,000","salaryMinSnippet":"AED90,000",
             "salaryMax":"120000","salaryMaxSnippet":"AED120,000",
             "baseSalaryMode":null,
             "bonusValue":"25","bonusValueSnippet":"Bonus: 25% of total fixed",
             "bonusBasis":"PERCENT_OF_TOTAL_FIXED","bonusBasisSnippet":"25% of total fixed",
             "benefits":[
               {"name":"Housing allowance","amount":"100,000","frequency":"YEARLY",
                "snippet":"Housing: 100,000 per year"},
               {"name":"Transport allowance","amount":"400","frequency":"MONTHLY",
                "snippet":"Transport: 400 per month"}]}
            """;

    @Test
    @DisplayName("reads the CHRO spec's package: band, currency, bonus and both allowances with their amounts")
    void readsTheStatedPackage() throws Exception {
        Fixture f = fixture("Compensation Package Firm");

        ProposedCompensation proposed = proposerWith(new RecordingChatModel(CHRO_ANSWER))
                .propose(UUID.randomUUID(), DOCUMENT_TEXT, f.clientId(), f.workspaceId());

        assertThat(proposed.source()).isEqualTo(ExtractionSource.MODEL);
        assertThat(valuesOf(proposed, "currency")).containsExactly("AED");
        assertThat(valuesOf(proposed, "salaryMin")).containsExactly("90000");
        assertThat(valuesOf(proposed, "salaryMax")).containsExactly("120000");
        assertThat(valuesOf(proposed, "baseSalaryMode")).isEmpty();
        assertThat(valuesOf(proposed, "bonusValue")).containsExactly("25");
        assertThat(valuesOf(proposed, "bonusBasis")).containsExactly("PERCENT_OF_TOTAL_FIXED");
        assertThat(valuesOf(proposed, "benefit"))
                .containsExactly("Housing allowance — yearly — 100000", "Transport allowance — monthly — 400");
    }

    @Test
    @DisplayName("a bonus past its basis's ceiling is dropped rather than proposed into a refused autosave")
    void dropsABonusPastItsCeiling() throws Exception {
        Fixture f = fixture("Compensation Ceiling Firm");
        RecordingChatModel model = new RecordingChatModel("""
                {"currency":"AED","bonusValue":"150","bonusBasis":"PERCENT_OF_BASE"}
                """);

        ProposedCompensation proposed = proposerWith(model)
                .propose(UUID.randomUUID(), DOCUMENT_TEXT, f.clientId(), f.workspaceId());

        assertThat(valuesOf(proposed, "bonusValue")).isEmpty();
        assertThat(valuesOf(proposed, "bonusBasis")).containsExactly("PERCENT_OF_BASE");
    }

    @Test
    @DisplayName("an allowance stated without an amount or a period keeps its name alone")
    void keepsAnUnquantifiedAllowanceByName() throws Exception {
        Fixture f = fixture("Compensation Allowance Firm");
        RecordingChatModel model = new RecordingChatModel("""
                {"currency":null,"benefits":[{"name":"Medical insurance"},
                  {"name":"School fees","amount":"30000"}]}
                """);

        ProposedCompensation proposed = proposerWith(model)
                .propose(UUID.randomUUID(), DOCUMENT_TEXT, f.clientId(), f.workspaceId());

        assertThat(valuesOf(proposed, "benefit"))
                .containsExactly("Medical insurance", "School fees — unstated — 30000");
    }

    @Test
    @DisplayName("a model that cannot be reached is an empty NONE reading, never an estimate")
    void fallsBackToNoneWhenTheModelFails() throws Exception {
        Fixture f = fixture("Compensation Fallback Firm");

        ProposedCompensation proposed = proposerWith(new ThrowingChatModel())
                .propose(UUID.randomUUID(), DOCUMENT_TEXT, f.clientId(), f.workspaceId());

        assertThat(proposed.source()).isEqualTo(ExtractionSource.NONE);
        assertThat(proposed.fields()).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PositionCompensationProposer proposerWith(ChatModel model) {
        return new PositionCompensationProposer(TestLlmCallPolicy.promptsOver(model), redactor, fieldReader,
                budgetGuard());
    }

    /** A guard whose limiter always says yes: the budget is metered in {@code LlmBudgetGuard}'s own test. */
    private static LlmBudgetGuard budgetGuard() {
        return new LlmBudgetGuard((key, limit, window) -> true,
                new LightMoveProperties(null, null, null, null, null,
                        new LlmSettings(new LlmRateLimitSettings(true, 10, 20, 10), 20_000, 1, List.of()),
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    private static List<String> valuesOf(ProposedCompensation proposed, String key) {
        return proposed.fields().stream()
                .filter(field -> field.fieldKey().equals(key))
                .map(ExtractedField::value)
                .toList();
    }

    private record Fixture(UUID workspaceId, UUID clientId) {}

    private Fixture fixture(String firmName) throws Exception {
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", "alok@" + this.domain), firmName);
        String admin = login("alok@" + this.domain);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Gulf Motors Group","customDomain":"gulfmotors.example"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return new Fixture(UUID.fromString(workspaceId), UUID.fromString(clientId));
    }

    /** Answers a fixed reply and keeps what it was asked, mirroring {@code PositionContextProposerTest}'s. */
    private static final class RecordingChatModel implements ChatModel {

        private final String reply;
        private final List<String> prompts = new ArrayList<>();

        private RecordingChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatOptions getOptions() {
            return GoogleGenAiChatOptions.builder()
                    .model("gemini-2.5-flash").temperature(0.8).maxOutputTokens(8192)
                    .labels(Map.of("app", "lightmove-api"))
                    .build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            StringBuilder text = new StringBuilder();
            for (Message message : prompt.getInstructions()) {
                text.append(message.getText()).append('\n');
            }
            prompts.add(text.toString());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    private static final class ThrowingChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new IllegalStateException("403 . There are issues with your billing account");
        }
    }
}
