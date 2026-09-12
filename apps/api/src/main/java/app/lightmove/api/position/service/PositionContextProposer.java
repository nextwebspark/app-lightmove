package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelContextAnswer;
import app.lightmove.api.position.model.ModelContextAnswer.ModelStrategicPriority;
import app.lightmove.api.position.model.ProposedMandateContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private static final int PRIORITY_MAX_COUNT = 20;

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, matching PositionDetailsProposer's own exemption.
    public PositionContextProposer(ChatClient chatClient,
                                   PositionDocumentRedactor redactor,
                                   @Value("classpath:prompts/position-extract-context-system.st") Resource systemPrompt,
                                   @Value("classpath:prompts/position-extract-context-schema.json") Resource answerSchema,
                                   LlmCallPolicy llmCalls,
                                   LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.redactor = redactor;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedMandateContext propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        llmBudget.requireContextExtractionBudget(userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelContextAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Mandate context extraction blocked before reaching the model: the "
                            + "document matched the injection word list.");
                }
                return empty();
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            // Deliberately broad and deliberately quiet, exactly as PositionDetailsProposer's catch
            // is: every way this call can fail has the same right answer, an honest empty reading.
            log.warn("Mandate context extraction found nothing to propose: {}", e.toString());
            return empty();
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
        List<ExtractedField> fields = new ArrayList<>();
        enumFieldFrom("mandateReason", MandateReason.class, answered.mandateReason(),
                answered.mandateReasonSnippet(), pseudonyms, originalText).ifPresent(fields::add);
        fieldFrom("businessDriver", answered.businessDriver(), answered.businessDriverSnippet(),
                pseudonyms, originalText).ifPresent(fields::add);

        if (answered.strategicPriorities() != null) {
            Set<String> seenCaseInsensitive = new LinkedHashSet<>();
            for (ModelStrategicPriority priority : answered.strategicPriorities()) {
                if (priority == null) {
                    continue;
                }
                fieldFrom("strategicPriority", priority.name(), priority.snippet(), pseudonyms, originalText)
                        .ifPresent(field -> {
                            // Never let the proposal itself carry a case-insensitive duplicate: a
                            // single "Accept all" must not be able to trip PositionService's own
                            // duplicate-name refusal on its own.
                            if (seenCaseInsensitive.add(field.value().toLowerCase(Locale.ROOT))) {
                                fields.add(field);
                            }
                        });
            }
        }
        return new ProposedMandateContext(ExtractionSource.MODEL, fields);
    }

    private Optional<ExtractedField> fieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                               Pseudonyms pseudonyms, String originalText) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = pseudonyms.rehydrate(rawValue);
        String snippet = rawSnippet == null || rawSnippet.isBlank() ? null : pseudonyms.rehydrate(rawSnippet);
        if (pseudonyms.hasResidue(value) || pseudonyms.hasResidue(snippet)) {
            log.warn("Mandate context extraction dropped a {} field: a placeholder survived re-hydration.",
                    fieldKey);
            return Optional.empty();
        }
        ProposalConfidence confidence = ProposalConfidence.MEDIUM;
        if (snippet != null && !occursIn(snippet, originalText)) {
            snippet = null;
            confidence = ProposalConfidence.LOW;
        }
        return Optional.of(new ExtractedField(fieldKey, value.trim(), confidence, snippet));
    }

    private <T extends Enum<T>> Optional<ExtractedField> enumFieldFrom(String fieldKey, Class<T> type,
                                                                        String rawValue, String rawSnippet,
                                                                        Pseudonyms pseudonyms, String originalText) {
        return fieldFrom(fieldKey, rawValue, rawSnippet, pseudonyms, originalText).flatMap(field -> {
            // Never Enum.valueOf: the model may answer a token this enum does not carry, and that
            // answer is dropped rather than thrown.
            T resolved = enumFromName(type, field.value());
            return resolved == null
                    ? Optional.empty()
                    : Optional.of(new ExtractedField(fieldKey, resolved.name(), field.confidence(), field.snippet()));
        });
    }

    private static <T extends Enum<T>> T enumFromName(Class<T> type, String token) {
        for (T value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(token.trim())) {
                return value;
            }
        }
        return null;
    }

    private static boolean occursIn(String snippet, String originalText) {
        String normalisedSnippet = normaliseWhitespace(snippet);
        return !normalisedSnippet.isEmpty()
                && normaliseWhitespace(originalText).toLowerCase(Locale.ROOT)
                        .contains(normalisedSnippet.toLowerCase(Locale.ROOT));
    }

    private static String normaliseWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static ProposedMandateContext finish(ProposedMandateContext proposed) {
        return new ProposedMandateContext(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Pre-truncates every value to {@code PutMandateContextRequest}'s own ceilings, so accepting a
     * proposal can never 400 the autosave it is handed to.
     */
    private static List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int priorityCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "businessDriver" -> truncated.add(capped(field, BUSINESS_DRIVER_MAX_LENGTH));
                case "strategicPriority" -> {
                    if (priorityCount < PRIORITY_MAX_COUNT) {
                        truncated.add(capped(field, PRIORITY_NAME_MAX_LENGTH));
                        priorityCount++;
                    }
                }
                default -> truncated.add(field);
            }
        }
        return truncated;
    }

    private static ExtractedField capped(ExtractedField field, int maxLength) {
        if (field.value().length() <= maxLength) {
            return field;
        }
        return new ExtractedField(field.fieldKey(), field.value().substring(0, maxLength),
                field.confidence(), field.snippet());
    }
}
