package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
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
import java.util.UUID;
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
 * Reads a freshly researched profile for what it says about the executive's background — nationality,
 * gender and years of experience — the three fields the report's Diversity chapter counts and a
 * researcher would otherwise have to type in by hand (issue #458).
 *
 * <p>Built on the shared {@link ChatClient} the way {@code ColumnMappingProposer} and
 * {@code PositionDetailsProposer} are. Unlike those, there is no deterministic heuristic behind this
 * one: nationality and gender have no reliable rule beyond a guess, and a career history's free-text
 * {@code period} ("2021–Present", "c. 2015") is exactly the kind of thing a regex gets wrong more often
 * than it helps. An outage, a refused call, or a budget already spent all answer the same way — nothing
 * inferred, the profile returned exactly as it came in — never a fabricated value standing in for a
 * real one.
 *
 * <p><b>Every value this call answers is written but flagged, never trusted outright.</b>
 * {@code Candidate.enrich} stamps whichever of these three fields it fills from here into
 * {@code aiInferredFields}, and only a researcher's own edit that actually changes the value clears the
 * flag. Gender in particular carried a "recorded, never inferred" rule before this feature existed;
 * the flag is what keeps that promise in spirit even though the rule itself no longer holds literally
 * — nothing this call answers is presented as a fact somebody entered until somebody has looked at it.
 */
@Service
@Slf4j
public class CandidateBackgroundProposer {

    private static final String PROMPT_ID = "candidate-background-infer";
    private static final double INFERENCE_TEMPERATURE = 0.0;
    private static final String ANSWER_MIME_TYPE = "application/json";

    /** No reasoning step: this is a read-and-answer task over a short profile, not one thinking improves. */
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

    /**
     * The same research, with whatever background this call could read from it folded in. Nothing the
     * vendor already answered is at risk here: only the three fields this call owns are ever set, and
     * every way this can fail returns {@code fetched} exactly as it came in.
     */
    public EnrichedProfile propose(UUID userId, String candidateFullName, EnrichedProfile fetched) {
        if (hasNothingToReasonFrom(fetched)) {
            return fetched;
        }
        try {
            llmBudget.require(LlmBudget.CANDIDATE_BACKGROUND_INFER, userId);
            ModelAnswer answered = ask(candidateFullName, fetched);
            if (answered == null || wasBlocked(answered)) {
                return fetched;
            }
            return fetched.withBackground(nationalityOf(answered), genderOf(answered),
                    yearsExperienceOf(answered));
        } catch (RuntimeException e) {
            // Every way this can fail — no budget left, no credentials, a network that cannot reach
            // Vertex, an answer that will not bind — has the same right answer: infer nothing this
            // time. The candidate simply keeps what the vendor's own research already gave it, and a
            // future re-capture gets another attempt.
            log.warn("Candidate background inference skipped: {}", e.toString());
            return fetched;
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

    /** No title, no about text, no career and no location leaves nothing to reason about — and
     * nothing worth a billed call answering three fields null. */
    private static boolean hasNothingToReasonFrom(EnrichedProfile fetched) {
        return fetched.title() == null && fetched.about() == null
                && fetched.career().isEmpty() && fetched.locationCountry() == null;
    }

    private static boolean wasBlocked(ModelAnswer answered) {
        return BlockedAnswer.matches(answered.nationality());
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
