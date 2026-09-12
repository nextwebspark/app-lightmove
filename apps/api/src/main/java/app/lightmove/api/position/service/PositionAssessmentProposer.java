package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.CompetencyPanel;
import app.lightmove.api.position.constant.CriterionMode;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelAssessmentAnswer;
import app.lightmove.api.position.model.ModelAssessmentAnswer.ModelCompetency;
import app.lightmove.api.position.model.ModelAssessmentAnswer.ModelCriterion;
import app.lightmove.api.position.model.PositionTemplate;
import app.lightmove.api.position.model.PositionTemplateCompetency;
import app.lightmove.api.position.model.ProposedAssessment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-five proposals — the structural twin of
 * {@link PositionCompensationProposer}, simplified the same way: no heuristic reader backs this one
 * up, because there is no key-value header for a screening criterion and a heuristic built to guess
 * one would be the exact invention this feature exists to refuse.
 *
 * <p>The one thing no other proposer does: it resolves the mandate's matching role template (the same
 * lookup {@link HeuristicBriefReader#readSeniority} already reuses) and, when one matches, sends its
 * competency names — that template's alone, never the whole library — to the model as a controlled
 * vocabulary, so seventeen mandates do not grow seventeen spellings of "Stakeholder management".
 */
@Service
@Slf4j
public class PositionAssessmentProposer {

    private static final String PROMPT_ID = "position-extract-assessment";
    private static final double EXTRACTION_TEMPERATURE = 0.0;
    private static final String ANSWER_MIME_TYPE = "application/json";
    private static final int EXTRACTION_THINKING_BUDGET = 0;

    /** Binds to {@link ModelAssessmentAnswer}, whose only required field is {@code criteria}. */
    private static final String BLOCKED =
            "{\"criteria\":[{\"text\":\"" + BlockedAnswer.MARKER + "\",\"mode\":\"REQUIRED\"}]}";

    private static final int CRITERION_TEXT_MAX_LENGTH = 300;
    private static final int CRITERIA_MAX_COUNT = 30;
    private static final int COMPETENCY_NAME_MAX_LENGTH = 120;
    private static final int COMPETENCY_DESCRIPTION_MAX_LENGTH = 300;
    private static final int COMPETENCY_MAX_COUNT_PER_PANEL = 10;

    /** The em-dash convention {@code PositionCompensationProposer.benefitFieldFrom} already packs a
     * benefit's optional frequency into, extended to a competency's two optional attributes: a
     * proposed value is {@code "<name>"}, {@code "<name> — <weight>"} or
     * {@code "<name> — <weight> — <description>"} — never {@code "<name> — <description>"} alone, so
     * the frontend's positional parser can always trust the second segment, when present, to be the
     * weight. */
    private static final String PACK_SEPARATOR = " — ";

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final PositionTemplateService templates;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, matching every other proposer's own exemption.
    public PositionAssessmentProposer(ChatClient chatClient,
                                      PositionDocumentRedactor redactor,
                                      PositionTemplateService templates,
                                      @Value("classpath:prompts/position-extract-assessment-system.st") Resource systemPrompt,
                                      @Value("classpath:prompts/position-extract-assessment-schema.json") Resource answerSchema,
                                      LlmCallPolicy llmCalls,
                                      LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.redactor = redactor;
        this.templates = templates;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedAssessment propose(UUID userId, String documentText, UUID clientId, UUID workspaceId,
                                      String roleTitle) {
        llmBudget.requireAssessmentExtractionBudget(userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            String vocabulary = vocabularyParagraph(workspaceId, roleTitle);
            ModelAssessmentAnswer answered = ask(redaction.text(), vocabulary);
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Assessment extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return empty();
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            log.warn("Assessment extraction found nothing to propose: {}", e.toString());
            return empty();
        }
    }

    private ModelAssessmentAnswer ask(String redactedText, String vocabulary) {
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
                        {vocabulary}
                        """)
                        .param("text", redactedText)
                        .param("vocabulary", vocabulary))
                .call()
                .entity(ModelAssessmentAnswer.class);
    }

    /**
     * The matched template's own competency names, grouped by panel — that template's alone. Empty
     * when the role title is blank, nothing matches, or the matched template names no competencies at
     * all, so the prompt carries no stray heading when there is nothing to list.
     */
    private String vocabularyParagraph(UUID workspaceId, String roleTitle) {
        if (roleTitle == null || roleTitle.isBlank()) {
            return "";
        }
        return templates.matching(workspaceId, roleTitle).map(this::vocabularyOf).orElse("");
    }

    private String vocabularyOf(PositionTemplate template) {
        List<PositionTemplateCompetency> competencies = template.getBody().competencies();
        String technicalNames = namesOf(competencies, CompetencyPanel.TECHNICAL);
        String behaviouralNames = namesOf(competencies, CompetencyPanel.BEHAVIOURAL);
        if (technicalNames.isEmpty() && behaviouralNames.isEmpty()) {
            return "";
        }
        StringBuilder vocabulary = new StringBuilder("This role's usual competency names:\n");
        if (!technicalNames.isEmpty()) {
            vocabulary.append("Technical: ").append(technicalNames).append('\n');
        }
        if (!behaviouralNames.isEmpty()) {
            vocabulary.append("Behavioural: ").append(behaviouralNames).append('\n');
        }
        return vocabulary.toString();
    }

    private static String namesOf(List<PositionTemplateCompetency> competencies, CompetencyPanel panel) {
        return competencies.stream()
                .filter(competency -> competency.panel() == panel)
                .map(PositionTemplateCompetency::name)
                .collect(Collectors.joining(", "));
    }

    private static boolean wasBlocked(ModelAssessmentAnswer answered) {
        List<ModelCriterion> criteria = answered.criteria();
        return criteria != null && !criteria.isEmpty() && BlockedAnswer.matches(criteria.get(0).text());
    }

    private static ProposedAssessment empty() {
        return new ProposedAssessment(ExtractionSource.NONE, List.of());
    }

    private ProposedAssessment reconcile(ModelAssessmentAnswer answered, Pseudonyms pseudonyms,
                                         String originalText) {
        List<ExtractedField> fields = new ArrayList<>();

        if (answered.criteria() != null) {
            for (ModelCriterion criterion : answered.criteria()) {
                if (criterion != null) {
                    criterionFieldFrom(criterion, pseudonyms, originalText).ifPresent(fields::add);
                }
            }
        }
        addCompetencies(fields, answered.technical(), "technicalCompetency", pseudonyms, originalText);
        addCompetencies(fields, answered.behavioural(), "behaviouralCompetency", pseudonyms, originalText);

        return new ProposedAssessment(ExtractionSource.MODEL, fields);
    }

    /**
     * A criterion's field key carries its mode ({@code requiredCriterion} / {@code preferredCriterion})
     * rather than packing {@code mode} into the value — the classification the issue calls "most of the
     * value of this story" reads straight off the key with no parsing on either side.
     */
    private Optional<ExtractedField> criterionFieldFrom(ModelCriterion criterion, Pseudonyms pseudonyms,
                                                        String originalText) {
        if (criterion.mode() == null) {
            return Optional.empty();
        }
        CriterionMode mode = enumFromName(CriterionMode.class, criterion.mode());
        if (mode == null) {
            return Optional.empty();
        }
        String fieldKey = mode == CriterionMode.REQUIRED ? "requiredCriterion" : "preferredCriterion";
        return fieldFrom(fieldKey, criterion.text(), criterion.snippet(), pseudonyms, originalText);
    }

    private void addCompetencies(List<ExtractedField> fields, List<ModelCompetency> competencies,
                                 String fieldKey, Pseudonyms pseudonyms, String originalText) {
        if (competencies == null) {
            return;
        }
        for (ModelCompetency competency : competencies) {
            if (competency != null) {
                competencyFieldFrom(fieldKey, competency, pseudonyms, originalText).ifPresent(fields::add);
            }
        }
    }

    /**
     * The whole row is dropped when the model states a weight that will not parse or falls outside
     * 0-100 — an answer worth discarding, not coercing. A weight the document simply never
     * suggested is a different, expected case: the competency is still proposed, with the weight
     * segment left out of the packed value entirely.
     */
    private Optional<ExtractedField> competencyFieldFrom(String fieldKey, ModelCompetency competency,
                                                          Pseudonyms pseudonyms, String originalText) {
        Integer weight = null;
        if (competency.weight() != null && !competency.weight().isBlank()) {
            try {
                weight = Integer.parseInt(competency.weight().trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (weight < 0 || weight > 100) {
                return Optional.empty();
            }
        }
        Integer resolvedWeight = weight;
        return fieldFrom(fieldKey, competency.name(), competency.snippet(), pseudonyms, originalText)
                .map(field -> new ExtractedField(fieldKey,
                        pack(field.value(), resolvedWeight, descriptionOf(competency, pseudonyms)),
                        field.confidence(), field.snippet()));
    }

    /** A tainted description is dropped on its own — it does not cost the competency its name and weight. */
    private String descriptionOf(ModelCompetency competency, Pseudonyms pseudonyms) {
        String raw = competency.description();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = pseudonyms.rehydrate(raw).trim();
        if (pseudonyms.hasResidue(value)) {
            log.warn("Assessment extraction dropped a competency description: a placeholder survived "
                    + "re-hydration.");
            return null;
        }
        return value;
    }

    /**
     * When a description is present but no weight was proposed, the weight segment is still emitted
     * as {@code "0"} rather than omitted — the frontend's parser trusts the second " — " segment,
     * when present, to be the weight, and omitting it here would silently shift the description into
     * that slot and lose it. {@code 0} reads as "not weighted yet", the same conservative default a
     * reviewer would set themselves.
     */
    private static String pack(String name, Integer weight, String description) {
        boolean hasDescription = description != null && !description.isBlank();
        if (weight == null && !hasDescription) {
            return name;
        }
        String weightToken = weight == null ? "0" : String.valueOf(weight);
        String packed = name + PACK_SEPARATOR + weightToken;
        return hasDescription ? packed + PACK_SEPARATOR + description : packed;
    }

    private Optional<ExtractedField> fieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                               Pseudonyms pseudonyms, String originalText) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = pseudonyms.rehydrate(rawValue);
        String snippet = rawSnippet == null || rawSnippet.isBlank() ? null : pseudonyms.rehydrate(rawSnippet);
        if (pseudonyms.hasResidue(value) || pseudonyms.hasResidue(snippet)) {
            log.warn("Assessment extraction dropped a {} field: a placeholder survived re-hydration.",
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

    private static ProposedAssessment finish(ProposedAssessment proposed) {
        return new ProposedAssessment(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Pre-truncates every value to {@code PutCriteriaRequest}'s and {@code PutCompetenciesRequest}'s
     * own ceilings, so accepting a proposal can never 400 the autosave it is handed to.
     */
    private static List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int criteriaCount = 0;
        int technicalCount = 0;
        int behaviouralCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "requiredCriterion", "preferredCriterion" -> {
                    if (criteriaCount < CRITERIA_MAX_COUNT) {
                        truncated.add(capped(field, CRITERION_TEXT_MAX_LENGTH));
                        criteriaCount++;
                    }
                }
                case "technicalCompetency" -> {
                    if (technicalCount < COMPETENCY_MAX_COUNT_PER_PANEL) {
                        truncated.add(cappedCompetency(field));
                        technicalCount++;
                    }
                }
                case "behaviouralCompetency" -> {
                    if (behaviouralCount < COMPETENCY_MAX_COUNT_PER_PANEL) {
                        truncated.add(cappedCompetency(field));
                        behaviouralCount++;
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

    /**
     * Caps a packed competency value's name and description segments independently, rather than
     * truncating the packed string as a whole — a flat cut could sever it mid-weight or mid-separator.
     * Split with a limit of 3 so a description that itself contains " — " is never chopped at the
     * wrong occurrence.
     */
    private static ExtractedField cappedCompetency(ExtractedField field) {
        String[] parts = field.value().split(PACK_SEPARATOR, 3);
        String name = parts[0].length() > COMPETENCY_NAME_MAX_LENGTH
                ? parts[0].substring(0, COMPETENCY_NAME_MAX_LENGTH)
                : parts[0];
        StringBuilder value = new StringBuilder(name);
        if (parts.length > 1) {
            value.append(PACK_SEPARATOR).append(parts[1]);
            if (parts.length > 2) {
                String description = parts[2].length() > COMPETENCY_DESCRIPTION_MAX_LENGTH
                        ? parts[2].substring(0, COMPETENCY_DESCRIPTION_MAX_LENGTH)
                        : parts[2];
                value.append(PACK_SEPARATOR).append(description);
            }
        }
        return new ExtractedField(field.fieldKey(), value.toString(), field.confidence(), field.snippet());
    }
}
