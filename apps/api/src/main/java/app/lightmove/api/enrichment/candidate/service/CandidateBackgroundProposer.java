package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateResearchedEvent;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.common.constant.NationalityGroup;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Reads a researched profile for the background a researcher would otherwise type in — nationality (one
 * of the nine groups), gender and years of experience. Every failure answers {@link InferredBackground#NONE}.
 */
@Service
@Slf4j
public class CandidateBackgroundProposer {

    private static final String PROMPT_ID = "candidate-background-infer";
    private static final double INFERENCE_TEMPERATURE = 0.0;
    private static final String ANSWER_MIME_TYPE = "application/json";

    private static final int INFERENCE_THINKING_BUDGET = 0;

    private static final int MIN_PLAUSIBLE_YEARS = 0;
    private static final int MAX_PLAUSIBLE_YEARS = 60;

    private static final String BLOCKED = "{\"nationality\":\"" + BlockedAnswer.MARKER + "\"}";

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written: Lombok cannot put @Value on a generated constructor parameter.
    public CandidateBackgroundProposer(ChatClient chatClient,
                                       @Value("classpath:prompts/candidate-background-infer-system.st")
                                       Resource systemPrompt,
                                       @Value("classpath:prompts/candidate-background-infer-schema.json")
                                       Resource answerSchema,
                                       LlmCallPolicy llmCalls,
                                       LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    /** Only the fields the event names as missing are answered; the rest come back null. */
    public InferredBackground propose(CandidateResearchedEvent event) {
        EnrichedProfile research = event.research();
        if (hasNothingToReasonFrom(research)) {
            return InferredBackground.NONE;
        }
        try {
            llmBudget.require(LlmBudget.CANDIDATE_BACKGROUND_INFER, event.addedBy());
            ModelAnswer answered = ask(event.fullName(), research);
            if (answered == null || BlockedAnswer.matches(answered.nationality())) {
                return InferredBackground.NONE;
            }
            Set<BackgroundField> missing = event.missing();
            return new InferredBackground(
                    missing.contains(BackgroundField.NATIONALITY) ? nationalityOf(answered) : null,
                    missing.contains(BackgroundField.GENDER) ? genderOf(answered) : null,
                    missing.contains(BackgroundField.YEARS_EXPERIENCE) ? yearsExperienceOf(answered) : null);
        } catch (RuntimeException e) {
            // No budget left, no credentials, Vertex unreachable, an answer that will not bind: all
            // mean infer nothing this time, and the candidate keeps what the research gave it.
            log.warn("Candidate background inference skipped: {}", e.toString());
            return InferredBackground.NONE;
        }
    }

    private ModelAnswer ask(String candidateFullName, EnrichedProfile fetched) {
        return chatClient.prompt()
                .advisors(guarded)
                .options(GoogleGenAiChatOptions.builder()
                        .temperature(INFERENCE_TEMPERATURE)
                        .responseMimeType(ANSWER_MIME_TYPE)
                        .thinkingBudget(INFERENCE_THINKING_BUDGET)
                        .labels(Map.of("prompt", PROMPT_ID)))
                .system(systemPrompt)
                .user(user -> user.text("""
                        Name: {name}
                        Current title: {title}
                        Location: {location}
                        About: {about}

                        Career history, most recent first:
                        {career}
                        """)
                        .param("name", orNotStated(candidateFullName))
                        .param("title", orNotStated(fetched.title()))
                        .param("location", locationOf(fetched))
                        .param("about", orNotStated(fetched.about()))
                        .param("career", careerOf(fetched.career())))
                .call()
                .entity(ModelAnswer.class);
    }

    /** Nothing worth a billed call answering three nulls. */
    private static boolean hasNothingToReasonFrom(EnrichedProfile fetched) {
        return fetched.title() == null && fetched.about() == null
                && fetched.career().isEmpty() && fetched.locationCountry() == null;
    }

    /** One of the nine canonical groups, or null — the model's own spelling is never stored as-is. */
    private static String nationalityOf(ModelAnswer answered) {
        NationalityGroup group = NationalityGroup.ofLabel(answered.nationality());
        return group == null ? null : group.value();
    }

    private static Gender genderOf(ModelAnswer answered) {
        return answered.gender() == null ? null
                : Gender.fromValue(answered.gender().trim().toLowerCase(Locale.ROOT));
    }

    /** Discards an implausible figure rather than storing a hallucinated one as though it were sound. */
    private static Integer yearsExperienceOf(ModelAnswer answered) {
        Integer years = answered.yearsExperience();
        if (years == null || years < MIN_PLAUSIBLE_YEARS || years > MAX_PLAUSIBLE_YEARS) {
            return null;
        }
        return years;
    }

    private static String orNotStated(String value) {
        return value == null ? "not stated" : value;
    }

    private static String locationOf(EnrichedProfile fetched) {
        if (fetched.locationCity() == null && fetched.locationCountry() == null) {
            return "not stated";
        }
        return Stream.of(fetched.locationCity(), fetched.locationCountry())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private static String careerOf(List<CandidateCareerEntry> career) {
        if (career.isEmpty()) {
            return "not stated";
        }
        return career.stream()
                .map(post -> "- %s at %s (%s)".formatted(
                        post.title() == null ? "unknown role" : post.title(),
                        post.company() == null ? "unknown company" : post.company(),
                        post.period() == null ? "period unknown" : post.period()))
                .collect(Collectors.joining("\n"));
    }

    /** The model's raw reply, bound before any of it is validated against this feature's vocabulary. */
    private record ModelAnswer(String nationality, String gender, Integer yearsExperience) {}
}
