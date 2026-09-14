package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelReportingAnswer.ModelDirectReport;
import app.lightmove.api.position.model.ModelReportingAnswer;
import app.lightmove.api.position.model.ProposedReportingStructure;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Asks the model to read a position description into step-three proposals — the structural twin of
 * {@link PositionCompensationProposer}: no heuristic reader backs this one up, and any failure
 * degrades to an honest empty reading rather than a guess.
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
 * <p>A field neither the document nor the model found is, last, offered from the mandate's matched
 * brief template, exactly as {@link PositionDetailsProposer#finish} does for step one — see
 * {@link #backfillFromTemplate}.
 */
@Service
@Slf4j
public class PositionReportingProposer {

    private static final String LABEL = "Reporting extraction";
    private static final String PROMPT_ID = "position-extract-reporting";
    private static final double EXTRACTION_TEMPERATURE = 0.0;
    private static final String ANSWER_MIME_TYPE = "application/json";
    private static final int EXTRACTION_THINKING_BUDGET = 0;

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

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final PositionTemplateService templates;
    private final ExtractedFieldReader fieldReader;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, matching every other proposer's own exemption.
    public PositionReportingProposer(ChatClient chatClient,
                                     PositionDocumentRedactor redactor,
                                     PositionTemplateService templates,
                                     ExtractedFieldReader fieldReader,
                                     @Value("classpath:prompts/position-extract-reporting-system.st") Resource systemPrompt,
                                     @Value("classpath:prompts/position-extract-reporting-schema.json") Resource answerSchema,
                                     LlmCallPolicy llmCalls,
                                     LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.redactor = redactor;
        this.templates = templates;
        this.fieldReader = fieldReader;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedReportingStructure propose(UUID userId, String documentText, UUID clientId, UUID workspaceId,
                                              String roleTitle) {
        llmBudget.require(LlmBudget.REPORTING_EXTRACT, userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelReportingAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Reporting extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return finish(empty(), workspaceId, roleTitle);
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText), workspaceId, roleTitle);
        } catch (RuntimeException e) {
            log.warn("Reporting extraction found nothing to propose: {}", e.toString());
            return finish(empty(), workspaceId, roleTitle);
        }
    }

    private ModelReportingAnswer ask(String redactedText) {
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
                .entity(ModelReportingAnswer.class);
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
        nonNegativeIntFieldFrom("noticeValue", answered.noticeValue(), answered.noticeValueSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "noticeUnit", NoticeUnit.class, answered.noticeUnit(),
                answered.noticeUnitSnippet(), pseudonyms, haystack).ifPresent(fields::add);

        return new ProposedReportingStructure(ExtractionSource.MODEL, fields);
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

    private ProposedReportingStructure finish(ProposedReportingStructure proposed, UUID workspaceId,
                                              String roleTitle) {
        List<ExtractedField> withTemplateBackfill = backfillFromTemplate(proposed.fields(), workspaceId, roleTitle);
        return new ProposedReportingStructure(proposed.source(), truncateToCeilings(withTemplateBackfill));
    }

    /**
     * Proposes the matched template's own {@code reportsTo}, {@code directReports}, {@code
     * noticeValue} and {@code noticeUnit} for whichever the document said nothing about — never {@code
     * teamSize}, which no template carries, the same rule compensation's salary numbers follow. Needs
     * the mandate's own persisted role title, since unlike step one this proposer never reads one out
     * of the document itself. {@code directReportTitle} is group-checked like step one's {@code
     * responsibility}: one document-sourced report suppresses the whole template list rather than
     * topping it up.
     */
    private List<ExtractedField> backfillFromTemplate(List<ExtractedField> fields, UUID workspaceId,
                                                       String roleTitle) {
        if (roleTitle == null || roleTitle.isBlank()) {
            return fields;
        }
        Optional<PositionTemplate> matched = templates.matching(workspaceId, roleTitle);
        if (matched.isEmpty()) {
            return fields;
        }
        PositionTemplateBody body = matched.get().getBody();
        Set<String> present = fields.stream().map(ExtractedField::fieldKey).collect(Collectors.toSet());

        List<ExtractedField> backfilled = new ArrayList<>(fields);
        addIfMissing(backfilled, present, "reportsToTitle", body.reportsTo());
        addIfMissing(backfilled, present, "noticeValue",
                body.noticeValue() == null ? null : body.noticeValue().toString());
        addIfMissing(backfilled, present, "noticeUnit",
                body.noticeUnit() == null ? null : body.noticeUnit().name());
        if (!present.contains("directReportTitle")) {
            body.directReports().stream()
                    .filter(text -> text != null && !text.isBlank())
                    .forEach(text -> backfilled.add(new ExtractedField("directReportTitle", text,
                            ProposalConfidence.LOW, null, ProposalOrigin.TEMPLATE)));
        }
        return backfilled;
    }

    private static void addIfMissing(List<ExtractedField> fields, Set<String> present, String fieldKey,
                                     String value) {
        if (present.contains(fieldKey) || value == null || value.isBlank()) {
            return;
        }
        fields.add(new ExtractedField(fieldKey, value, ProposalConfidence.LOW, null, ProposalOrigin.TEMPLATE));
    }

    /**
     * Pre-truncates every value to {@code PutReportingStructureRequest}'s and {@code OrgNodeDto}'s own
     * ceilings, so accepting a proposal — and the client-side chart merge it feeds — can never 400 the
     * autosave it is handed to. {@code directReportTitle} is already bounded to
     * {@link #DIRECT_REPORT_MAX_COUNT} before {@link #reconcile} runs; the count here is defence in
     * depth against a template backfill topping it back up, not the load-bearing cap.
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
