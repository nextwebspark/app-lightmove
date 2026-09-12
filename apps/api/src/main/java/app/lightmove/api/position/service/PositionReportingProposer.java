package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.NoticeUnit;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelReportingAnswer;
import app.lightmove.api.position.model.ModelReportingAnswer.ModelDirectReport;
import app.lightmove.api.position.model.ProposedReportingStructure;
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
 */
@Service
@Slf4j
public class PositionReportingProposer {

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
     * is enforced client-side, where the existing chart's size is actually known.
     */
    private static final int DIRECT_REPORT_MAX_COUNT = 40;

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, matching every other proposer's own exemption.
    public PositionReportingProposer(ChatClient chatClient,
                                     PositionDocumentRedactor redactor,
                                     @Value("classpath:prompts/position-extract-reporting-system.st") Resource systemPrompt,
                                     @Value("classpath:prompts/position-extract-reporting-schema.json") Resource answerSchema,
                                     LlmCallPolicy llmCalls,
                                     LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.redactor = redactor;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedReportingStructure propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        llmBudget.requireReportingExtractionBudget(userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelReportingAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Reporting extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return empty();
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            log.warn("Reporting extraction found nothing to propose: {}", e.toString());
            return empty();
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
        List<ExtractedField> fields = new ArrayList<>();

        fieldFrom("reportsToTitle", answered.reportsToTitle(), answered.reportsToTitleSnippet(),
                pseudonyms, originalText).ifPresent(fields::add);

        if (answered.directReports() != null) {
            for (ModelDirectReport directReport : answered.directReports()) {
                if (directReport != null) {
                    fieldFrom("directReportTitle", directReport.title(), directReport.snippet(),
                            pseudonyms, originalText).ifPresent(fields::add);
                }
            }
        }

        fieldFrom("teamSize", answered.teamSize(), answered.teamSizeSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        nonNegativeIntFieldFrom("noticeValue", answered.noticeValue(), answered.noticeValueSnippet(),
                pseudonyms, originalText).ifPresent(fields::add);
        enumFieldFrom("noticeUnit", NoticeUnit.class, answered.noticeUnit(), answered.noticeUnitSnippet(),
                pseudonyms, originalText).ifPresent(fields::add);

        return new ProposedReportingStructure(ExtractionSource.MODEL, fields);
    }

    /** Parsed as a non-negative whole number; unparsable or negative is dropped, never clamped to zero. */
    private Optional<ExtractedField> nonNegativeIntFieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                                             Pseudonyms pseudonyms, String originalText) {
        return fieldFrom(fieldKey, rawValue, rawSnippet, pseudonyms, originalText).flatMap(field -> {
            try {
                int value = Integer.parseInt(field.value().trim().replaceAll("[,\\s]", ""));
                return value < 0
                        ? Optional.empty()
                        : Optional.of(new ExtractedField(fieldKey, Integer.toString(value), field.confidence(),
                                field.snippet()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private Optional<ExtractedField> fieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                               Pseudonyms pseudonyms, String originalText) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = pseudonyms.rehydrate(rawValue);
        String snippet = rawSnippet == null || rawSnippet.isBlank() ? null : pseudonyms.rehydrate(rawSnippet);
        if (pseudonyms.hasResidue(value) || pseudonyms.hasResidue(snippet)) {
            log.warn("Reporting extraction dropped a {} field: a placeholder survived re-hydration.", fieldKey);
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

    private static ProposedReportingStructure finish(ProposedReportingStructure proposed) {
        return new ProposedReportingStructure(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Pre-truncates every value to {@code PutReportingStructureRequest}'s and {@code OrgNodeDto}'s own
     * ceilings, so accepting a proposal — and the client-side chart merge it feeds — can never 400 the
     * autosave it is handed to.
     */
    private static List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int directReportCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "reportsToTitle" -> truncated.add(capped(field, TITLE_MAX_LENGTH));
                case "directReportTitle" -> {
                    if (directReportCount < DIRECT_REPORT_MAX_COUNT) {
                        truncated.add(capped(field, TITLE_MAX_LENGTH));
                        directReportCount++;
                    }
                }
                case "teamSize" -> truncated.add(capped(field, TEAM_SIZE_MAX_LENGTH));
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
