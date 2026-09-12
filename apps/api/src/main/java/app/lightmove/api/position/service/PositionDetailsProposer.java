package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelDetailsAnswer;
import app.lightmove.api.position.model.ModelDetailsAnswer.ModelResponsibility;
import app.lightmove.api.position.model.ProposedPositionDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-one proposals — the structural twin of
 * {@code dataimport}'s {@code ColumnMappingProposer}: the same shared {@code ChatClient}, a system
 * prompt and structured answer of its own, and the same three call options a lookup-shaped task
 * wants that the shortlist prompt beside it does not.
 *
 * <p><b>The document is redacted before it is sent, and re-hydrated after.</b> See {@link
 * PositionDocumentRedactor} for what is removed and why. Every value and snippet the model returns is
 * re-hydrated; a surviving placeholder after re-hydration means the field is dropped rather than
 * surfaced, and a snippet that does not literally occur in the original document is dropped and its
 * field's confidence downgraded rather than trusted as a quote.
 *
 * <p><b>The heuristic reader never seeds this call.</b> {@link HeuristicBriefReader}'s answer stays
 * local: it is the fallback when the model cannot be reached or is blocked, and it feeds exactly one
 * cross-check — where the model and the heuristic agree on the role title, that field's confidence is
 * upgraded, because two independent readings landing on the same title is worth more than either
 * alone.
 */
@Service
@Slf4j
public class PositionDetailsProposer {

    private static final String PROMPT_ID = "position-extract-details";

    /** Extraction has one right answer per field, so variance only buys answers that will not bind. */
    private static final double EXTRACTION_TEMPERATURE = 0.0;

    /** See {@code ColumnMappingProposer}'s identical constant: native JSON avoids a markdown fence. */
    private static final String ANSWER_MIME_TYPE = "application/json";

    /** No reasoning step: reading a document into fixed fields is not a problem thinking improves. */
    private static final int EXTRACTION_THINKING_BUDGET = 0;

    /**
     * What the guard answers with when it blocks a call. Binds to {@link ModelDetailsAnswer}, whose
     * only required field is {@code roleTitle} — the same reason the schema requires it.
     */
    private static final String BLOCKED = "{\"roleTitle\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final int ROLE_TITLE_MAX_LENGTH = 160;
    private static final int DEPARTMENT_MAX_LENGTH = 160;
    private static final int LOCATION_MAX_LENGTH = 120;
    private static final int NARRATIVE_MAX_LENGTH = 4000;
    private static final int RESPONSIBILITY_MAX_LENGTH = 200;
    private static final int RESPONSIBILITY_MAX_COUNT = 20;

    private final ChatClient chatClient;
    private final HeuristicBriefReader heuristics;
    private final PositionDocumentRedactor redactor;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, matching ColumnMappingProposer's own exemption.
    public PositionDetailsProposer(ChatClient chatClient,
                                   HeuristicBriefReader heuristics,
                                   PositionDocumentRedactor redactor,
                                   @Value("classpath:prompts/position-extract-details-system.st") Resource systemPrompt,
                                   @Value("classpath:prompts/position-extract-details-schema.json") Resource answerSchema,
                                   LlmCallPolicy llmCalls,
                                   LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.heuristics = heuristics;
        this.redactor = redactor;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedPositionDetails propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        // Run first and kept local: the heuristic's reading never reaches the prompt, but it is both
        // the ultimate fallback and the one cross-check a model answer gets.
        ProposedPositionDetails heuristic = heuristics.propose(workspaceId, documentText);

        llmBudget.requirePositionExtractionBudget(userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelDetailsAnswer answered = ask(redaction.text());
            if (answered == null) {
                return finish(heuristic);
            }
            if (wasBlocked(answered)) {
                log.warn("Position extraction blocked before reaching the model: the document matched "
                        + "the injection word list. Falling back to the heuristic reader.");
                return finish(heuristic);
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText, heuristic));
        } catch (RuntimeException e) {
            // Deliberately broad and deliberately quiet, exactly as ColumnMappingProposer's catch is:
            // every way this call can fail has the same right answer, the heuristic's own reading, and
            // the response says which of the two produced it rather than claiming the model did.
            log.warn("Position extraction fell back to the heuristic reader: {}", e.toString());
            return finish(heuristic);
        }
    }

    private ModelDetailsAnswer ask(String redactedText) {
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
                .entity(ModelDetailsAnswer.class);
    }

    private static boolean wasBlocked(ModelDetailsAnswer answered) {
        return BlockedAnswer.matches(answered.roleTitle());
    }

    private ProposedPositionDetails reconcile(ModelDetailsAnswer answered, Pseudonyms pseudonyms,
                                              String originalText, ProposedPositionDetails heuristic) {
        List<ExtractedField> fields = new ArrayList<>();
        fieldFrom("roleTitle", answered.roleTitle(), answered.roleTitleSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        fieldFrom("department", answered.department(), answered.departmentSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        fieldFrom("location", answered.location(), answered.locationSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        enumFieldFrom("employmentType", EmploymentType.class, answered.employmentType(),
                answered.employmentTypeSnippet(), pseudonyms, originalText).ifPresent(fields::add);
        enumFieldFrom("seniority", Seniority.class, answered.seniority(),
                answered.senioritySnippet(), pseudonyms, originalText).ifPresent(fields::add);
        fieldFrom("narrative", answered.narrative(), answered.narrativeSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        if (answered.responsibilities() != null) {
            for (ModelResponsibility responsibility : answered.responsibilities()) {
                if (responsibility == null) {
                    continue;
                }
                fieldFrom("responsibility", responsibility.text(), responsibility.snippet(), pseudonyms, originalText)
                        .ifPresent(fields::add);
            }
        }
        return new ProposedPositionDetails(ExtractionSource.MODEL,
                upgradeRoleTitleIfCorroborated(fields, heuristic));
    }

    /**
     * Re-hydrates a raw value and snippet, sweeps a redaction leak, and downgrades a snippet that does
     * not literally occur in the source. Empty for a field the model left null — leaving it null was
     * the model saying it found nothing, which is the correct answer to carry forward, not a value to
     * invent.
     */
    private Optional<ExtractedField> fieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                               Pseudonyms pseudonyms, String originalText) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = pseudonyms.rehydrate(rawValue);
        String snippet = rawSnippet == null || rawSnippet.isBlank() ? null : pseudonyms.rehydrate(rawSnippet);
        if (pseudonyms.hasResidue(value) || pseudonyms.hasResidue(snippet)) {
            // Rule 3: any surviving placeholder is a redaction leak, and the whole field is dropped
            // rather than shown with a placeholder in it — the failure that would make this look broken.
            log.warn("Position extraction dropped a {} field: a placeholder survived re-hydration.", fieldKey);
            return Optional.empty();
        }
        ProposalConfidence confidence = ProposalConfidence.MEDIUM;
        if (snippet != null && !occursIn(snippet, originalText)) {
            // Rule 4: a re-hydrated snippet absent from the original text is a paraphrase, not a
            // quote — the snippet is dropped and confidence downgraded, but the value itself stands.
            snippet = null;
            confidence = ProposalConfidence.LOW;
        }
        return Optional.of(new ExtractedField(fieldKey, value.trim(), confidence, snippet));
    }

    private <T extends Enum<T>> Optional<ExtractedField> enumFieldFrom(String fieldKey, Class<T> type,
                                                                        String rawValue, String rawSnippet,
                                                                        Pseudonyms pseudonyms, String originalText) {
        return fieldFrom(fieldKey, rawValue, rawSnippet, pseudonyms, originalText).flatMap(field -> {
            // Never Enum.valueOf: the model may answer a token this enum does not carry ("Permanent",
            // "C-Level"), and that answer is dropped rather than thrown.
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

    /** Two independent readings landing on the same title outrank either reading alone. */
    private static List<ExtractedField> upgradeRoleTitleIfCorroborated(List<ExtractedField> fields,
                                                                        ProposedPositionDetails heuristic) {
        Optional<String> heuristicTitle = heuristic.fields().stream()
                .filter(field -> field.fieldKey().equals("roleTitle"))
                .map(ExtractedField::value)
                .findFirst();
        if (heuristicTitle.isEmpty()) {
            return fields;
        }
        return fields.stream()
                .map(field -> field.fieldKey().equals("roleTitle")
                        && normaliseWhitespace(field.value()).equalsIgnoreCase(normaliseWhitespace(heuristicTitle.get()))
                        ? field.withConfidence(ProposalConfidence.HIGH)
                        : field)
                .toList();
    }

    private static ProposedPositionDetails finish(ProposedPositionDetails proposed) {
        return new ProposedPositionDetails(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Pre-truncates every value to {@code PutPositionDetailsRequest}'s own ceilings, on both the model
     * and the heuristic path, so accepting a proposal can never 400 the autosave it is handed to.
     */
    private static List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int responsibilityCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "roleTitle" -> truncated.add(capped(field, ROLE_TITLE_MAX_LENGTH));
                case "department" -> truncated.add(capped(field, DEPARTMENT_MAX_LENGTH));
                case "location" -> truncated.add(capped(field, LOCATION_MAX_LENGTH));
                case "narrative" -> truncated.add(capped(field, NARRATIVE_MAX_LENGTH));
                case "responsibility" -> {
                    if (responsibilityCount < RESPONSIBILITY_MAX_COUNT) {
                        truncated.add(capped(field, RESPONSIBILITY_MAX_LENGTH));
                        responsibilityCount++;
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
