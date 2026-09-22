package app.lightmove.api.companydiscovery.service;

import app.lightmove.api.companydiscovery.constant.DiscoveryMode;
import app.lightmove.api.companydiscovery.model.DiscoveredCandidate;
import app.lightmove.api.companydiscovery.model.DiscoveryAnswer;
import app.lightmove.api.companydiscovery.model.DiscoveryQuery;
import app.lightmove.api.companydiscovery.model.ModelDiscoveryAnswer;
import app.lightmove.api.core.config.CompanyDiscoverySettings;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.Resource;

/**
 * Gemini with Google Search grounding — the Spring-native answer to "search the web", with no second
 * HTTP client and no new dependency.
 *
 * <p>Two things about the provider shape this class, both checked against the 2.0.1 jars rather than
 * the documentation.
 *
 * <p><b>Vertex has historically refused grounding and a response schema on one call.</b> So there
 * are two paths: one grounded structured call, and — where that is refused — grounded prose followed
 * by a second, <b>ungrounded</b>, call that reads the prose into the schema. Two billed calls
 * instead of one, which is why the first is tried at all. Dropping grounding to keep the schema is
 * not on the menu: a discovery answer with no web access is a different product wearing the same
 * button.
 *
 * <p><b>Spring AI does not surface grounding metadata.</b> Nothing in the provider copies
 * {@code groundingMetadata} onto a {@code Generation}, so there is no citation list to read off the
 * response. Source URLs come back inside the model's own answer or not at all, and a missing one
 * stays missing rather than being invented.
 */
@Slf4j
public class GeminiCompanyDiscovery implements CompanyDiscovery {

    static final String PROMPT_ID = "company-discovery";
    static final String PROSE_PROMPT_ID = "company-discovery-prose";
    static final String EXTRACT_PROMPT_ID = "company-discovery-extract";

    private static final String ANSWER_MIME_TYPE = "application/json";

    /**
     * Replies in place of the model, so it has to bind to {@link ModelDiscoveryAnswer} — a sentence
     * would surface as a parse error indistinguishable from Vertex being unreachable.
     */
    private static final String BLOCKED =
            "{\"companies\":[{\"companyName\":\"" + BlockedAnswer.MARKER + "\"}]}";

    private final ChatClient chatClient;
    private final CompanyDiscoverySettings settings;
    private final Resource groundedPrompt;
    private final Resource prosePrompt;
    private final Resource extractPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guardedStructured;
    private final Consumer<ChatClient.AdvisorSpec> guardedProse;
    private final Consumer<ChatClient.AdvisorSpec> guardedExtract;

    /**
     * Latched off for the life of the process the first time Vertex refuses grounding beside a
     * schema. Purely a cost optimisation — see {@link #looksLikeSchemaWithGroundingRefusal}.
     */
    private volatile boolean structuredGroundingViable;

    public GeminiCompanyDiscovery(ChatClient chatClient, CompanyDiscoverySettings settings,
                                  Resource groundedPrompt, Resource prosePrompt,
                                  Resource extractPrompt, Resource answerSchema,
                                  LlmCallPolicy llmCalls) {
        this.chatClient = chatClient;
        this.settings = settings;
        this.groundedPrompt = groundedPrompt;
        this.prosePrompt = prosePrompt;
        this.extractPrompt = extractPrompt;
        // Resolved here rather than per call: a schema that will not load fails the context at
        // startup instead of every request that needed it.
        this.guardedStructured =
                llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.guardedProse = llmCalls.forPrompt(PromptGuardSpec.prose(PROSE_PROMPT_ID));
        this.guardedExtract = llmCalls.forPrompt(
                PromptGuardSpec.structured(EXTRACT_PROMPT_ID, answerSchema, BLOCKED));
        this.structuredGroundingViable = settings.groundedStructuredOutput();
    }

    @Override
    public DiscoveryAnswer discover(DiscoveryQuery query) {
        if (structuredGroundingViable) {
            try {
                return new DiscoveryAnswer(candidates(askGroundedStructured(query)),
                        DiscoveryMode.GROUNDED_STRUCTURED);
            } catch (RuntimeException e) {
                if (!looksLikeSchemaWithGroundingRefusal(e)) {
                    return unavailable(e);
                }
                structuredGroundingViable = false;
                log.warn("Vertex refused grounding beside a response schema; this process will use "
                        + "grounded prose plus a second extraction from now on. Set "
                        + "lightmove.company.discovery.grounded-structured-output: false to skip the "
                        + "probe. Refusal: {}", e.toString());
            }
        }

        try {
            String prose = askGroundedProse(query);
            if (prose == null || prose.isBlank()) {
                return DiscoveryAnswer.none(DiscoveryMode.GROUNDED_PROSE_EXTRACTED);
            }
            return new DiscoveryAnswer(candidates(askUngroundedExtraction(prose)),
                    DiscoveryMode.GROUNDED_PROSE_EXTRACTED);
        } catch (RuntimeException e) {
            return unavailable(e);
        }
    }

    @Override
    public String provider() {
        return "gemini-grounded";
    }

    /**
     * One grounded call that also names a schema. The cheap path when the region allows it.
     */
    private ModelDiscoveryAnswer askGroundedStructured(DiscoveryQuery query) {
        return chatClient.prompt()
                .advisors(guardedStructured)
                .options(baseOptions(PROMPT_ID)
                        .responseMimeType(ANSWER_MIME_TYPE)
                        .googleSearchRetrieval(true))
                .system(groundedPrompt)
                .user(user -> user.text(QUESTION).param("question", query.question())
                        .param("country", constraint(query.country()))
                        .param("limit", String.valueOf(query.limit())))
                .call()
                .entity(ModelDiscoveryAnswer.class);
    }

    /** Grounded, and free to answer in prose. */
    private String askGroundedProse(DiscoveryQuery query) {
        return chatClient.prompt()
                .advisors(guardedProse)
                .options(baseOptions(PROSE_PROMPT_ID).googleSearchRetrieval(true))
                .system(prosePrompt)
                .user(user -> user.text(QUESTION).param("question", query.question())
                        .param("country", constraint(query.country()))
                        .param("limit", String.valueOf(query.limit())))
                .call()
                .content();
    }

    /**
     * Reads that prose into the schema, with <b>no</b> web access. It has nothing to look up and
     * nothing to add — its whole job is transcription, and the prompt says so.
     */
    private ModelDiscoveryAnswer askUngroundedExtraction(String prose) {
        return chatClient.prompt()
                .advisors(guardedExtract)
                .options(baseOptions(EXTRACT_PROMPT_ID)
                        .responseMimeType(ANSWER_MIME_TYPE)
                        .thinkingBudget(0))
                .system(extractPrompt)
                .user(user -> user.text("""
                        Read the companies out of this text:

                        {prose}
                        """).param("prose", prose))
                .call()
                .entity(ModelDiscoveryAnswer.class);
    }

    private GoogleGenAiChatOptions.Builder baseOptions(String promptId) {
        return GoogleGenAiChatOptions.builder()
                .model(settings.model())
                .temperature(settings.temperature())
                .thinkingBudget(settings.thinkingBudget())
                .labels(Map.of("prompt", promptId));
    }

    private static final String QUESTION = """
            Market question: {question}
            Country constraint: {country}
            Return at most {limit} companies.
            """;

    private static String constraint(String country) {
        return country == null || country.isBlank() ? "none" : country;
    }

    /**
     * Whether a failure was Vertex refusing the schema-plus-grounding pair rather than anything else.
     *
     * <p>String-sniffing, and it has to be: the provider wraps every SDK failure in a bare
     * {@code RuntimeException}, so there is no type to switch on. <b>This is a cost optimisation and
     * never a correctness gate.</b> Guessing wrong in either direction lands on the same prose path
     * or the same empty answer; all it decides is whether the next request pays for the probe again.
     */
    private static boolean looksLikeSchemaWithGroundingRefusal(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            boolean rejected = lower.contains("invalid_argument") || lower.contains("400");
            boolean namesTheSchema = lower.contains("response_schema")
                    || lower.contains("responseschema")
                    || lower.contains("response_mime_type")
                    || lower.contains("responsemimetype")
                    || lower.contains("tools");
            if (rejected && namesTheSchema) {
                return true;
            }
        }
        return false;
    }

    private DiscoveryAnswer unavailable(RuntimeException failure) {
        // Deliberately broad and deliberately quiet, like every other caller of this client: no
        // credentials, no quota, an unreachable Vertex and an answer that will not bind all have the
        // same right answer. There is no local fallback here on purpose — a keyword scan of our own
        // universe returned under an "AI Research" heading would be indistinguishable to the user
        // from a web search while being its exact opposite.
        log.warn("Company discovery answered nothing: {}", failure.toString());
        return DiscoveryAnswer.none(DiscoveryMode.UNAVAILABLE);
    }

    private static List<DiscoveredCandidate> candidates(ModelDiscoveryAnswer answered) {
        if (answered == null || answered.companies() == null) {
            return List.of();
        }
        if (wasBlocked(answered)) {
            // The guard replied instead of the model. Its own line, because it degrades exactly as an
            // outage does and they are not the same event to whoever reads the log — and a market
            // question is far likelier to trip the word list than a spreadsheet header is.
            log.warn("Company discovery blocked before reaching the model: the question matched the "
                    + "injection word list.");
            return List.of();
        }
        List<DiscoveredCandidate> candidates = new ArrayList<>(answered.companies().size());
        for (ModelDiscoveryAnswer.Proposed proposed : answered.companies()) {
            if (proposed == null || proposed.companyName() == null
                    || proposed.companyName().isBlank()) {
                continue;
            }
            candidates.add(new DiscoveredCandidate(proposed.companyName().trim(),
                    proposed.linkedinUrl(), proposed.websiteUrl(), proposed.sourceUrl(),
                    oneLine(proposed.reason()), proposed.fit()));
        }
        return candidates;
    }

    private static boolean wasBlocked(ModelDiscoveryAnswer answered) {
        return answered.companies().size() == 1
                && answered.companies().getFirst() != null
                && BlockedAnswer.MARKER.equals(answered.companies().getFirst().companyName());
    }

    /** The reason lands in a grid cell and becomes a filed row's note; a paragraph breaks both. */
    private static String oneLine(String reason) {
        return reason == null ? null : reason.replaceAll("\\s+", " ").trim();
    }
}
