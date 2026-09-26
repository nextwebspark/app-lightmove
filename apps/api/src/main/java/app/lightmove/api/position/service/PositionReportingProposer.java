package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.NoticePeriod;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelReportingAnswer.ModelDirectReport;
import app.lightmove.api.position.model.ModelReportingAnswer;
import app.lightmove.api.position.model.ProposedReportingStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-three proposals; any failure degrades to an
 * empty reading rather than a guess.
 *
 * <p>It never answers with an org chart, only titles: replacing the chart would lose the canvas layout
 * and break {@code OrgChartRules.requireExactlyOneMandateSeat}, since the model cannot know which seat is
 * the mandate's. Merging titles into the chart is the frontend's job (issue #282). {@code reportsToTitle}
 * is a title, never a person's name.
 */
@Service
@Slf4j
public class PositionReportingProposer {

    private static final String LABEL = "Reporting extraction";
    private static final String PROMPT_ID = "position-extract-reporting";

    /** Binds to {@link ModelReportingAnswer}, whose only required field is {@code reportsToTitle}. */
    private static final String BLOCKED = "{\"reportsToTitle\":\"" + BlockedAnswer.MARKER + "\"}";

    /** Matches {@code OrgNodeDto.title}'s own ceiling, so an accepted proposal can never 400 the autosave. */
    private static final int TITLE_MAX_LENGTH = 160;
    private static final int TEAM_SIZE_MAX_LENGTH = 160;

    /**
     * Well short of {@code PutReportingStructureRequest}'s 60-seat cap, which must also fit the mandate
     * seat, the reports-to seat and the existing chart; the true cap is enforced client-side.
     */
    private static final int DIRECT_REPORT_MAX_COUNT = 40;

    private final PositionDocumentRedactor redactor;
    private final ExtractedFieldReader fieldReader;
    private final StructuredPrompt prompt;
    private final LlmBudgetGuard llmBudget;

    public PositionReportingProposer(StructuredPromptFactory prompts,
                                     PositionDocumentRedactor redactor,
                                     ExtractedFieldReader fieldReader,
                                     LlmBudgetGuard llmBudget) {
        this.redactor = redactor;
        this.fieldReader = fieldReader;
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
        this.llmBudget = llmBudget;
    }

    public ProposedReportingStructure propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        llmBudget.require(LlmBudget.REPORTING_EXTRACT, userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelReportingAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Reporting extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return finish(empty());
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            log.warn("Reporting extraction found nothing to propose: {}", e.toString());
            return finish(empty());
        }
    }

    private ModelReportingAnswer ask(String redactedText) {
        return prompt.ask(ModelReportingAnswer.class, user -> user.text("""
                Position description text:
                {text}
                """)
                .param("text", redactedText));
    }

    private static boolean wasBlocked(ModelReportingAnswer answered) {
        return BlockedAnswer.matches(answered.reportsToTitle());
    }

    private static ProposedReportingStructure empty() {
        return new ProposedReportingStructure(ExtractionSource.NONE, List.of());
    }

    private ProposedReportingStructure reconcile(ModelReportingAnswer answered, Pseudonyms pseudonyms,
                                                 String originalText) {
        String haystack = fieldReader.haystackOf(originalText);
        List<ExtractedField> fields = new ArrayList<>();

        fieldReader.fieldFrom(LABEL, "reportsToTitle", answered.reportsToTitle(), answered.reportsToTitleSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);

        if (answered.directReports() != null) {
            // Bounded before per-entry work: a model that ignores the schema's cap must not pay for
            // verifying entries that will only be discarded.
            for (ModelDirectReport directReport : answered.directReports().stream()
                    .limit(DIRECT_REPORT_MAX_COUNT).toList()) {
                if (directReport != null) {
                    fieldReader.fieldFrom(LABEL, "directReportTitle", directReport.title(), directReport.snippet(),
                            pseudonyms, haystack).ifPresent(fields::add);
                }
            }
        }

        fieldReader.fieldFrom(LABEL, "teamSize", answered.teamSize(), answered.teamSizeSnippet(), pseudonyms,
                haystack).ifPresent(fields::add);
        noticePeriodFieldFrom(answered, pseudonyms, haystack).ifPresent(fields::add);

        return new ProposedReportingStructure(ExtractionSource.MODEL, fields);
    }

    /**
     * Folds the model's count and unit into one of the offered notice periods; a pair that names no
     * option is dropped. The prompt asks for count and unit so the model reads rather than buckets.
     */
    private Optional<ExtractedField> noticePeriodFieldFrom(ModelReportingAnswer answered, Pseudonyms pseudonyms,
                                                           String haystack) {
        Optional<ExtractedField> count = nonNegativeIntFieldFrom("noticePeriod", answered.noticeValue(),
                answered.noticeValueSnippet(), pseudonyms, haystack);
        Optional<ExtractedField> unit = fieldReader.enumFieldFrom(LABEL, "noticePeriod", NoticeUnit.class,
                answered.noticeUnit(), answered.noticeUnitSnippet(), pseudonyms, haystack);
        if (count.isEmpty() || unit.isEmpty()) {
            return Optional.empty();
        }
        NoticePeriod period = NoticePeriod.ofPair(Integer.valueOf(count.get().value()),
                NoticeUnit.valueOf(unit.get().value()));
        return period == null
                ? Optional.empty()
                : Optional.of(new ExtractedField("noticePeriod", period.value(), count.get().confidence(),
                        count.get().snippet(), count.get().origin()));
    }

    /** Parsed as a non-negative whole number; unparsable or negative is dropped, never clamped to zero. */
    private Optional<ExtractedField> nonNegativeIntFieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                                             Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, fieldKey, rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            try {
                int value = Integer.parseInt(field.value().trim().replaceAll("[,\\s]", ""));
                return value < 0
                        ? Optional.empty()
                        : Optional.of(new ExtractedField(fieldKey, Integer.toString(value), field.confidence(),
                                field.snippet(), field.origin()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private ProposedReportingStructure finish(ProposedReportingStructure proposed) {
        return new ProposedReportingStructure(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /** Truncates to the write requests' own ceilings, so accepting a proposal can never 400 the autosave. */
    private List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int directReportCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "reportsToTitle" -> truncated.add(fieldReader.capped(field, TITLE_MAX_LENGTH));
                case "directReportTitle" -> {
                    if (directReportCount < DIRECT_REPORT_MAX_COUNT) {
                        truncated.add(fieldReader.capped(field, TITLE_MAX_LENGTH));
                        directReportCount++;
                    }
                }
                case "teamSize" -> truncated.add(fieldReader.capped(field, TEAM_SIZE_MAX_LENGTH));
                default -> truncated.add(field);
            }
        }
        return truncated;
    }
}
