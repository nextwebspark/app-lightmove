package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelContextAnswer.ModelStrategicPriority;
import app.lightmove.api.position.model.ModelContextAnswer;
import app.lightmove.api.position.model.ProposedMandateContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-two proposals, redacted as
 * {@link PositionDetailsProposer} does. There is no heuristic fallback — a mandate reason is never
 * stated as a keyword — so a failure answers {@link ExtractionSource#NONE}.
 */
@Service
@Slf4j
public class PositionContextProposer {

    private static final String PROMPT_ID = "position-extract-context";

    /** Binds to {@link ModelContextAnswer}, whose only required field is {@code mandateReason}. */
    private static final String BLOCKED = "{\"mandateReason\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final int BUSINESS_DRIVER_MAX_LENGTH = 1000;
    private static final int PRIORITY_NAME_MAX_LENGTH = 120;
    private static final int PRIORITY_MAX_COUNT = 5;
    private static final String LABEL = "Mandate context extraction";

    private final PositionDocumentRedactor redactor;
    private final ExtractedFieldReader fieldReader;
    private final StructuredPrompt prompt;
    private final LlmBudgetGuard llmBudget;

    public PositionContextProposer(StructuredPromptFactory prompts,
                                   PositionDocumentRedactor redactor,
                                   ExtractedFieldReader fieldReader,
                                   LlmBudgetGuard llmBudget) {
        this.redactor = redactor;
        this.fieldReader = fieldReader;
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
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
            // Deliberately broad: every failure has the same right answer, an empty reading.
            log.warn("Mandate context extraction found nothing to propose", e);
            return finish(empty());
        }
    }

    private ModelContextAnswer ask(String redactedText) {
        return prompt.ask(ModelContextAnswer.class, user -> user.text("""
                Position description text:
                {text}
                """)
                .param("text", redactedText));
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
                            // Never let a proposal trip PositionService's duplicate-name refusal.
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

    /** Truncates to {@code PutMandateContextRequest}'s ceilings, so accepting a proposal can never 400 the autosave. */
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
