package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateResearchedEvent;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.config.LlmSettings;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class CandidateBackgroundProposerTest {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile("Group CFO", null, "Al Rawabi Dairy",
            null, null, "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2011 – Present")),
            null, null, null, null, EnrichmentVendor.BRIGHTDATA);

    private static final Set<BackgroundField> ALL_MISSING = EnumSet.allOf(BackgroundField.class);

    @Test
    @DisplayName("a well-formed answer comes back as the three canonical values")
    void aWellFormedAnswerIsMapped() {
        InferredBackground proposed = proposerAnswering(
                "{\"nationality\":\"western EXPAT\",\"gender\":\"Female\",\"yearsExperience\":14}")
                .propose(researched(ALL_MISSING));

        assertThat(proposed).isEqualTo(new InferredBackground("Western expat", Gender.FEMALE, 14));
    }

    @Test
    @DisplayName("a nationality outside the nine groups and an implausible figure are dropped")
    void anOffVocabularyAnswerIsDropped() {
        InferredBackground proposed = proposerAnswering(
                "{\"nationality\":\"Egyptian\",\"gender\":\"male\",\"yearsExperience\":75}")
                .propose(researched(ALL_MISSING));

        assertThat(proposed).isEqualTo(new InferredBackground(null, Gender.MALE, null));
    }

    @Test
    @DisplayName("only the fields the event names as missing are answered")
    void onlyMissingFieldsAreAnswered() {
        InferredBackground proposed = proposerAnswering(
                "{\"nationality\":\"Saudi\",\"gender\":\"male\",\"yearsExperience\":14}")
                .propose(researched(EnumSet.of(BackgroundField.NATIONALITY)));

        assertThat(proposed).isEqualTo(new InferredBackground("Saudi", null, null));
    }

    @Test
    @DisplayName("a model that cannot be reached infers nothing")
    void aFailedCallInfersNothing() {
        CandidateBackgroundProposer proposer = proposerOver(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("Failed to get application default credentials");
            }
        });

        assertThat(proposer.propose(researched(ALL_MISSING))).isEqualTo(InferredBackground.NONE);
    }

    private static CandidateResearchedEvent researched(Set<BackgroundField> missing) {
        return new CandidateResearchedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Sample Person", missing, RESEARCH);
    }

    private static CandidateBackgroundProposer proposerAnswering(String reply) {
        return proposerOver(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
            }
        });
    }

    private static CandidateBackgroundProposer proposerOver(ChatModel model) {
        return new CandidateBackgroundProposer(ChatClient.builder(model).build(),
                new ByteArrayResource("infer the background".getBytes()),
                new ClassPathResource("prompts/candidate-background-infer-schema.json"),
                TestLlmCallPolicy.asShipped(),
                new LlmBudgetGuard((key, limit, window) -> true,
                        new LightMoveProperties(null, null, null, null, null,
                                new LlmSettings(new LlmRateLimitSettings(true, 10, 20, 10), 20_000, 1, List.of()),
                                null, null, null, null, null, null, null, null, null)));
    }
}
