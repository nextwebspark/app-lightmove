package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.common.constant.MandateReason;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelContextAnswer.ModelStrategicPriority;
import app.lightmove.api.position.model.ModelContextAnswer;
import app.lightmove.api.position.model.ProposedMandateContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-two proposals — the structural twin of
 * {@link PositionDetailsProposer}, simplified: no heuristic reader backs this one up.
 *
 * <p><b>There is no fallback reading.</b> Unlike step one, a key-value header block or a bulleted
 * section gives nothing useful here — {@code mandateReason} is never stated as a keyword, and a
 * heuristic built to guess one would be guessing, exactly what this feature exists to refuse to do. So
 * where {@link PositionDetailsProposer} falls back to {@code HeuristicBriefReader}, this one falls back
 * to nothing at all: an empty {@link ProposedMandateContext} labelled {@link ExtractionSource#NONE}.
 *
 * <p>The document is redacted before it is sent and re-hydrated after, exactly as
 * {@link PositionDetailsProposer} does — see {@link PositionDocumentRedactor}.
 *
 * <p>A field neither the document nor the model found stays unproposed. Earlier drafts backfilled
 * strategic priorities from the mandate's matched brief template — dead weight once a mandate is
 * already seeded from that same template, the same reasoning {@link PositionDetailsProposer}'s class
 * doc gives.
 */
@Service
@Slf4j
public class PositionContextProposer {

    private static final String PROMPT_ID = "position-extract-context";
    private static final double EXTRACTION_TEMPERATURE = 0.0;
    private static final String ANSWER_MIME_TYPE = "application/json";
    private static final int EXTRACTION_THINKING_BUDGET = 0;

    /** Binds to {@link ModelContextAnswer}, whose only required field is {@code mandateReason}. */
    private static final String BLOCKED = "{\"mandateReason\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final int BUSINESS_DRIVER_MAX_LENGTH = 1000;
    private static final int PRIORITY_NAME_MAX_LENGTH = 120;
    private static final int PRIORITY_MAX_COUNT = 5;
    private static final String LABEL = "Mandate context extraction";

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final ExtractedFieldReader fieldReader;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, matching PositionDetailsProposer's own exemption.
    public PositionContextProposer(ChatClient chatClient,
                                   PositionDocumentRedactor redactor,
                                   ExtractedFieldReader fieldReader,
                                   @Value("classpath:prompts/position-extract-context-system.st") Resource systemPrompt,
                                   @Value("classpath:prompts/position-extract-context-schema.json") Resource answerSchema,
                                   LlmCallPolicy llmCalls,
                                   LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.redactor = redactor;
        this.fieldReader = fieldReader;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedMandateContext propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        llmBudget.require(LlmBudget.CONTEXT_EXTRACT, userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelContextAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Mandate context extraction blocked before reaching the model: the "
                            + "document matched the injection word list.");
                }
                return finish(empty());
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            // Deliberately broad and deliberately quiet, exactly as PositionDetailsProposer's catch
            // is: every way this call can fail has the same right answer, an honest empty reading.
            log.warn("Mandate context extraction found nothing to propose", e);
            return finish(empty());
        }
    }

    private ModelContextAnswer ask(String redactedText) {
        return chatClient.prompt()
                .advisors(guarded)
                .options(GoogleGenAiChatOptions.builder()
                        .temperature(EXTRACTION_TEMPERATURE)
                        .responseMimeType(ANSWER_MIME_TYPE)
                        .thinkingBudget(EXTRACTION_THINKING_BUDGET)
                        .labels(Map.of("prompt", PROMPT_ID)))
                .system(systemPrompt)
                .user(user -> user.text("""
                        Position description text:
                        {text}
                        """)
                        .param("text", redactedText))
                .call()
                .entity(ModelContextAnswer.class);
    }

    private static boolean wasBlocked(ModelContextAnswer answered) {
        return BlockedAnswer.matches(answered.mandateReason());
    }

    private static ProposedMandateContext empty() {
        return new ProposedMandateContext(ExtractionSource.NONE, List.of());
    }

    private ProposedMandateContext reconcile(ModelContextAnswer answered, Pseudonyms pseudonyms,
                                             String originalText) {
        String haystack = fieldReader.haystackOf(originalText);
        List<ExtractedField> fields = new ArrayList<>();
        fieldReader.enumFieldFrom(LABEL, "mandateReason", MandateReason.class, answered.mandateReason(),
                answered.mandateReasonSnippet(), pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.fieldFrom(LABEL, "businessDriver", answered.businessDriver(), answered.businessDriverSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);

        if (answered.strategicPriorities() != null) {
            Set<String> seenCaseInsensitive = new LinkedHashSet<>();
            for (ModelStrategicPriority priority : answered.strategicPriorities()) {
                if (priority == null) {
                    continue;
                }
                fieldReader.fieldFrom(LABEL, "strategicPriority", priority.name(), priority.snippet(),
                        pseudonyms, haystack)
                        .ifPresent(field -> {
                            // Never let the proposal itself carry a case-insensitive duplicate: a
                            // single "Accept all" must not be able to trip PositionService's own
                            // duplicate-name refusal.
                            if (seenCaseInsensitive.add(field.value().toLowerCase(Locale.ROOT))) {
                                fields.add(field);
                            }
                        });
            }
        }
        return new ProposedMandateContext(ExtractionSource.MODEL, fields);
    }

    private ProposedMandateContext finish(ProposedMandateContext proposed) {
        return new ProposedMandateContext(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Pre-truncates every value to {@code PutMandateContextRequest}'s own ceilings, so accepting a
     * proposal can never 400 the autosave it is handed to.
     */
    private List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int priorityCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "businessDriver" -> truncated.add(fieldReader.capped(field, BUSINESS_DRIVER_MAX_LENGTH));
                case "strategicPriority" -> {
                    if (priorityCount < PRIORITY_MAX_COUNT) {
                        truncated.add(fieldReader.capped(field, PRIORITY_NAME_MAX_LENGTH));
                        priorityCount++;
                    }
                }
                default -> truncated.add(field);
            }
        }
        return truncated;
    }
}
