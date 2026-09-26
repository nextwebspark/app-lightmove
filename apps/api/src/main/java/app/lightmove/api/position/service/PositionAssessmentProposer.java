package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelAssessmentAnswer.ModelCompetency;
import app.lightmove.api.position.model.ModelAssessmentAnswer.ModelCriterion;
import app.lightmove.api.position.model.ModelAssessmentAnswer;
import app.lightmove.api.position.model.ProposedAssessment;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateCompetency;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-five proposals, sending the title-matched
 * template's competency names as a vocabulary so mandates converge on shared spellings.
 */
@Service
@Slf4j
public class PositionAssessmentProposer {

    private static final String LABEL = "Assessment extraction";
    private static final String PROMPT_ID = "position-extract-assessment";

    /** Binds to {@link ModelAssessmentAnswer}, whose only required field is {@code criteria}. */
    private static final String BLOCKED =
            "{\"criteria\":[{\"text\":\"" + BlockedAnswer.MARKER + "\",\"mode\":\"REQUIRED\"}]}";

    private static final int CRITERION_TEXT_MAX_LENGTH = 300;
    private static final int CRITERIA_MAX_COUNT = 5;
    private static final int COMPETENCY_NAME_MAX_LENGTH = 120;
    private static final int COMPETENCY_DESCRIPTION_MAX_LENGTH = 300;
    private static final int COMPETENCY_MAX_COUNT_PER_PANEL = 5;
    private static final int VOCABULARY_MAX_LENGTH = 2000;

    /** Packs {@code "<name>[ — <weight>[ — <description>]]"}; the frontend trusts segment 2 to be the weight. */
    private static final String PACK_SEPARATOR = " — ";

    private final PositionDocumentRedactor redactor;
    private final PositionTemplateService templates;
    private final ExtractedFieldReader fieldReader;
    private final StructuredPrompt prompt;
    private final LlmBudgetGuard llmBudget;

    public PositionAssessmentProposer(StructuredPromptFactory prompts,
                                      PositionDocumentRedactor redactor,
                                      PositionTemplateService templates,
                                      ExtractedFieldReader fieldReader,
                                      LlmBudgetGuard llmBudget) {
        this.redactor = redactor;
        this.templates = templates;
        this.fieldReader = fieldReader;
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
        this.llmBudget = llmBudget;
    }

    public ProposedAssessment propose(UUID userId, String documentText, UUID clientId, UUID workspaceId,
                                      String roleTitle) {
        llmBudget.require(LlmBudget.ASSESSMENT_EXTRACT, userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            String vocabulary = vocabularyParagraph(workspaceId, roleTitle);
            ModelAssessmentAnswer answered = ask(redaction.text(), vocabulary);
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Assessment extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return finish(empty());
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            log.warn("Assessment extraction found nothing to propose: {}", e.toString());
            return finish(empty());
        }
    }

    private ModelAssessmentAnswer ask(String redactedText, String vocabulary) {
        return prompt.ask(ModelAssessmentAnswer.class, user -> user.text("""
                Position description text:
                {text}
                {vocabulary}
                """)
                .param("text", redactedText)
                .param("vocabulary", vocabulary));
    }

    /**
     * Matched on the title alone, never the generic fallback {@link PositionTemplateService#matching}
     * offers, whose names are nobody's "usual". Empty when nothing matches.
     */
    private String vocabularyParagraph(UUID workspaceId, String roleTitle) {
        if (roleTitle == null || roleTitle.isBlank()) {
            return "";
        }
        return templates.matchingByTitle(workspaceId, roleTitle).map(this::vocabularyOf).orElse("");
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
        // A template name is workspace-writable text reaching the prompt without the document's
        // redaction or PromptGuardSpec wrapping, so it is bounded here, and a name carrying a newline or
        // a pseudonym-shaped token is dropped outright since either could forge a new prompt section.
        return vocabulary.length() > VOCABULARY_MAX_LENGTH
                ? vocabulary.substring(0, VOCABULARY_MAX_LENGTH)
                : vocabulary.toString();
    }

    private static String namesOf(List<PositionTemplateCompetency> competencies, CompetencyPanel panel) {
        return competencies.stream()
                .filter(competency -> competency.panel() == panel)
                .map(PositionTemplateCompetency::name)
                .filter(PositionAssessmentProposer::safeVocabularyName)
                .map(PositionAssessmentProposer::cappedVocabularyName)
                .collect(Collectors.joining(", "));
    }

    private static boolean safeVocabularyName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("\n") && !name.contains("[[") && !name.contains("]]");
    }

    private static String cappedVocabularyName(String name) {
        return name.length() > COMPETENCY_NAME_MAX_LENGTH ? name.substring(0, COMPETENCY_NAME_MAX_LENGTH) : name;
    }

    private static boolean wasBlocked(ModelAssessmentAnswer answered) {
        List<ModelCriterion> criteria = answered.criteria();
        if (criteria == null || criteria.isEmpty()) {
            return false;
        }
        ModelCriterion first = criteria.get(0);
        return first != null && BlockedAnswer.matches(first.text());
    }

    private static ProposedAssessment empty() {
        return new ProposedAssessment(ExtractionSource.NONE, List.of());
    }

    private ProposedAssessment reconcile(ModelAssessmentAnswer answered, Pseudonyms pseudonyms,
                                         String originalText) {
        String haystack = fieldReader.haystackOf(originalText);
        List<ExtractedField> fields = new ArrayList<>();

        if (answered.criteria() != null) {
            for (ModelCriterion criterion : answered.criteria()) {
                if (criterion != null) {
                    criterionFieldFrom(criterion, pseudonyms, haystack).ifPresent(fields::add);
                }
            }
        }
        addCompetencies(fields, answered.technical(), "technicalCompetency", pseudonyms, haystack);
        addCompetencies(fields, answered.behavioural(), "behaviouralCompetency", pseudonyms, haystack);

        return new ProposedAssessment(ExtractionSource.MODEL, fields);
    }

    /** The field key carries the criterion's mode ({@code requiredCriterion} / {@code preferredCriterion}). */
    private Optional<ExtractedField> criterionFieldFrom(ModelCriterion criterion, Pseudonyms pseudonyms,
                                                        String haystack) {
        if (criterion.mode() == null) {
            return Optional.empty();
        }
        CriterionMode mode = fieldReader.enumFromName(CriterionMode.class, criterion.mode());
        if (mode == null) {
            return Optional.empty();
        }
        String fieldKey = mode == CriterionMode.REQUIRED ? "requiredCriterion" : "preferredCriterion";
        return fieldReader.fieldFrom(LABEL, fieldKey, criterion.text(), criterion.snippet(), pseudonyms, haystack);
    }

    private void addCompetencies(List<ExtractedField> fields, List<ModelCompetency> competencies,
                                 String fieldKey, Pseudonyms pseudonyms, String haystack) {
        if (competencies == null) {
            return;
        }
        for (ModelCompetency competency : competencies) {
            if (competency != null) {
                competencyFieldFrom(fieldKey, competency, pseudonyms, haystack).ifPresent(fields::add);
            }
        }
    }

    /**
     * Drops the row when a stated weight will not parse or falls outside 0-100, or when the name contains
     * {@link #PACK_SEPARATOR} and would corrupt the packing. A missing weight is simply omitted.
     */
    private Optional<ExtractedField> competencyFieldFrom(String fieldKey, ModelCompetency competency,
                                                          Pseudonyms pseudonyms, String haystack) {
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
        return fieldReader.fieldFrom(LABEL, fieldKey, competency.name(), competency.snippet(), pseudonyms, haystack)
                .flatMap(field -> {
                    if (field.value().contains(PACK_SEPARATOR)) {
                        log.warn("Assessment extraction dropped a {} field: the competency name contained "
                                + "the packing separator.", fieldKey);
                        return Optional.empty();
                    }
                    return Optional.of(new ExtractedField(fieldKey,
                            pack(field.value(), resolvedWeight, descriptionOf(competency, pseudonyms)),
                            field.confidence(), field.snippet(), field.origin()));
                });
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
     * A description without a weight still emits weight {@code "0"}: the frontend reads segment 2 as the
     * weight, so omitting it would shift the description into that slot and lose it.
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

    private ProposedAssessment finish(ProposedAssessment proposed) {
        return new ProposedAssessment(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Truncates and counts to the write requests' ceilings. That bounds one proposal, not the brief:
     * {@code PositionPage.tsx} caps on the brief's remaining headroom at accept time.
     */
    private List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int criteriaCount = 0;
        int technicalCount = 0;
        int behaviouralCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "requiredCriterion", "preferredCriterion" -> {
                    if (criteriaCount < CRITERIA_MAX_COUNT) {
                        truncated.add(fieldReader.capped(field, CRITERION_TEXT_MAX_LENGTH));
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

    /**
     * Caps name and description separately — a flat cut could sever the packing — splitting with a limit
     * of 3 so a description containing the separator stays whole.
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
        return new ExtractedField(field.fieldKey(), value.toString(), field.confidence(), field.snippet(),
                field.origin());
    }
}
