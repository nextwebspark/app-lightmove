package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.LocationLine;
import app.lightmove.api.position.model.ModelDetailsAnswer.ModelResponsibility;
import app.lightmove.api.position.model.ModelDetailsAnswer;
import app.lightmove.api.position.model.ProposedPositionDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Reads a position description into step-one proposals, falling back to {@link HeuristicBriefReader}.
 * The document is redacted and re-hydrated ({@link PositionDocumentRedactor}); a surviving placeholder
 * or a non-literal snippet is dropped.
 */
@Service
@Slf4j
public class PositionDetailsProposer {

    private static final String PROMPT_ID = "position-extract-details";
    private static final String LABEL = "Position extraction";

    /** The guard's answer when it blocks a call; binds to {@link ModelDetailsAnswer}'s one required field. */
    private static final String BLOCKED = "{\"roleTitle\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final int ROLE_TITLE_MAX_LENGTH = 160;
    private static final int DEPARTMENT_MAX_LENGTH = 160;
    private static final int LOCATION_MAX_LENGTH = 120;
    private static final int NARRATIVE_MAX_LENGTH = 4000;
    private static final int RESPONSIBILITY_MAX_LENGTH = 200;
    private static final int RESPONSIBILITY_MAX_COUNT = 5;

    private final HeuristicBriefReader heuristics;
    private final PositionDocumentRedactor redactor;
    private final ExtractedFieldReader fieldReader;
    private final StructuredPrompt prompt;
    private final LlmBudgetGuard llmBudget;

    public PositionDetailsProposer(StructuredPromptFactory prompts,
                                   HeuristicBriefReader heuristics,
                                   PositionDocumentRedactor redactor,
                                   ExtractedFieldReader fieldReader,
                                   LlmBudgetGuard llmBudget) {
        this.heuristics = heuristics;
        this.redactor = redactor;
        this.fieldReader = fieldReader;
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
        this.llmBudget = llmBudget;
    }

    public ProposedPositionDetails propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        ProposedPositionDetails heuristic = heuristics.propose(workspaceId, documentText);

        llmBudget.require(LlmBudget.POSITION_EXTRACT, userId);

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
            // Deliberately broad: every failure has the same right answer, the heuristic's reading.
            log.warn("Position extraction fell back to the heuristic reader: {}", e.toString());
            return finish(heuristic);
        }
    }

    private ModelDetailsAnswer ask(String redactedText) {
        return prompt.ask(ModelDetailsAnswer.class, user -> user.text("""
                Position description text:
                {text}
                """)
                .param("text", redactedText));
    }

    private static boolean wasBlocked(ModelDetailsAnswer answered) {
        return BlockedAnswer.matches(answered.roleTitle());
    }

    private ProposedPositionDetails reconcile(ModelDetailsAnswer answered, Pseudonyms pseudonyms,
                                              String originalText, ProposedPositionDetails heuristic) {
        String haystack = fieldReader.haystackOf(originalText);

        List<ExtractedField> fields = new ArrayList<>();
        fieldReader.fieldFrom(LABEL, "roleTitle", answered.roleTitle(), answered.roleTitleSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.fieldFrom(LABEL, "department", answered.department(), answered.departmentSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.fieldFrom(LABEL, "location", answered.location(), answered.locationSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "employmentType", EmploymentType.class, answered.employmentType(),
                answered.employmentTypeSnippet(), pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "seniority", Seniority.class, answered.seniority(),
                answered.senioritySnippet(), pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.fieldFrom(LABEL, "narrative", answered.narrative(), answered.narrativeSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        if (answered.responsibilities() != null) {
            for (ModelResponsibility responsibility : answered.responsibilities()) {
                if (responsibility == null) {
                    continue;
                }
                fieldReader.fieldFrom(LABEL, "responsibility", responsibility.text(), responsibility.snippet(),
                        pseudonyms, haystack).ifPresent(fields::add);
            }
        }
        return new ProposedPositionDetails(ExtractionSource.MODEL,
                upgradeRoleTitleIfCorroborated(fields, heuristic));
    }

    /** Two independent readings landing on the same title outrank either reading alone. */
    private List<ExtractedField> upgradeRoleTitleIfCorroborated(List<ExtractedField> fields,
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
                        && fieldReader.normaliseWhitespace(field.value())
                                .equalsIgnoreCase(fieldReader.normaliseWhitespace(heuristicTitle.get()))
                        ? field.withConfidence(ProposalConfidence.HIGH)
                        : field)
                .toList();
    }

    private ProposedPositionDetails finish(ProposedPositionDetails proposed) {
        return new ProposedPositionDetails(proposed.source(), truncateToCeilings(splitLocation(proposed.fields())));
    }

    /**
     * Splits the one location line both readers produce into the brief's city and country (V66); a tail
     * the catalog cannot place stays whole as the city.
     */
    private static List<ExtractedField> splitLocation(List<ExtractedField> fields) {
        List<ExtractedField> split = new ArrayList<>();
        for (ExtractedField field : fields) {
            if (!field.fieldKey().equals("location")) {
                split.add(field);
                continue;
            }
            LocationLine line = LocationLine.of(field.value());
            if (line.isEmpty()) {
                continue;
            }
            if (line.city() != null) {
                split.add(new ExtractedField("locationCity", line.city(), field.confidence(),
                        field.snippet(), field.origin()));
            }
            if (line.country() != null) {
                split.add(new ExtractedField("locationCountry", line.country(), field.confidence(),
                        field.snippet(), field.origin()));
            }
        }
        return split;
    }

    /** Truncates to {@code PutPositionDetailsRequest}'s ceilings, so accepting a proposal can never 400 the autosave. */
    private List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int responsibilityCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "roleTitle" -> truncated.add(fieldReader.capped(field, ROLE_TITLE_MAX_LENGTH));
                case "department" -> truncated.add(fieldReader.capped(field, DEPARTMENT_MAX_LENGTH));
                case "locationCity", "locationCountry" ->
                        truncated.add(fieldReader.capped(field, LOCATION_MAX_LENGTH));
                case "narrative" -> truncated.add(fieldReader.capped(field, NARRATIVE_MAX_LENGTH));
                case "responsibility" -> {
                    if (responsibilityCount < RESPONSIBILITY_MAX_COUNT) {
                        truncated.add(fieldReader.capped(field, RESPONSIBILITY_MAX_LENGTH));
                        responsibilityCount++;
                    }
                }
                default -> truncated.add(field);
            }
        }
        return truncated;
    }
}
