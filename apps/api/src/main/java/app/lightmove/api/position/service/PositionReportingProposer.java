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
 * Asks the model to read a position description into step-three proposals — no heuristic reader backs
 * this one up, and any failure degrades to an honest empty reading rather than a guess.
 *
 * <p><b>This proposer never answers with an org chart.</b> The chart already on the brief carries
 * consultant-dragged canvas positions and exactly one seat flagged {@code mandateSeat}, and a proposal
 * that replaced it would violate {@code OrgChartRules.requireExactlyOneMandateSeat} — the model has no
 * way to know which seat that is — and would throw away the layout. So this only ever emits titles: a
 * {@code reportsToTitle} and a list of {@code directReportTitle} rows. Merging an accepted title into
 * the existing chart (renaming the current manager, minting one and re-parenting the mandate seat under
 * it, or appending a child under it) is the frontend's {@code orgChart.ts} merge helpers' job, not this
 * class's — see issue #282.
 *
 * <p>{@code reportsToTitle} is deliberately a title and never a person's name, even when the document
 * names one alongside their title ("Reporting to Ahmed Al-Mansoori, Group CEO") — the prompt is
 * explicit about this, and {@code name} is never populated by an accepted proposal.
 *
 * <p>A field neither the document nor the model found stays unproposed. Earlier drafts backfilled
 * reports-to, direct reports and notice period from the mandate's matched brief template — dead weight
 * once a mandate is already seeded from that same template, the same reasoning
 * {@link PositionDetailsProposer}'s class doc gives. {@code PositionExtractionService} separately
 * offers the matched template's usual direct reports as {@code usualDirectReports}, an explicit opt-in
 * rather than a silent proposal.
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
     * A generous ceiling on how many direct-report rows this proposer will hand back — well short of
     * {@code PutReportingStructureRequest}'s 60-seat chart cap, which also has to leave room for the
     * mandate seat, the reports-to seat and whatever the chart already held. The true 60-seat ceiling
     * is enforced client-side, where the existing chart's size is actually known. Applied to the
     * model's raw answer before any per-entry work, not after — the schema also caps {@code
     * directReports} at the same number, so this is defence in depth against a model that ignores it.
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
            // Bounded before any per-entry work, not after: the schema already caps this at the same
            // number, but a model that ignores it must not pay for redaction/verification on entries
            // that will only be discarded.
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
     * The count and the unit the model read, folded into the one period step three offers for them.
     *
     * <p>The prompt still asks for a number and a unit, because that is what a document states and
     * asking for one of five would make the model bucket rather than read. The fold happens here
     * instead, and a pair that names no option on offer is dropped the way an unparsable count and
     * an unrecognised unit already are — a proposal nobody can accept is worse than none.
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

    /**
     * Pre-truncates every value to {@code PutReportingStructureRequest}'s and {@code OrgNodeDto}'s own
     * ceilings, so accepting a proposal — and the client-side chart merge it feeds — can never 400 the
     * autosave it is handed to. {@code directReportTitle} is already bounded to
     * {@link #DIRECT_REPORT_MAX_COUNT} before {@link #reconcile} runs; the count here is defence in
     * depth, not the load-bearing cap.
     */
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
