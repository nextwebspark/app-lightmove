package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.BaseSalaryMode;
import app.lightmove.api.position.constant.BenefitFrequency;
import app.lightmove.api.position.constant.BonusBasis;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.IncentiveType;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelCompensationAnswer;
import app.lightmove.api.position.model.ModelCompensationAnswer.ModelBenefit;
import app.lightmove.api.position.model.PositionTemplate;
import app.lightmove.api.position.model.PositionTemplateBenefit;
import app.lightmove.api.position.model.ProposedCompensation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the model to read a position description into step-four proposals — the structural twin of
 * {@link PositionDetailsProposer}, simplified the same way {@link PositionContextProposer} is: no
 * heuristic reader backs this one up, because no sample document carries a package and a heuristic
 * built to guess one would be the exact invention this feature exists to refuse.
 *
 * <p>The headline guarantee: this proposer comes back empty far more often than it comes back with
 * anything — every numeric field is dropped rather than guessed at when the document does not clearly
 * state it, and nothing here ever estimates a market rate.
 *
 * <p>A field neither the document nor the model found is, last, offered from the mandate's matched
 * brief template — see {@link #backfillFromTemplate}. Never {@code salaryMin}/{@code salaryMax}/
 * {@code incentiveAmount}: {@code PositionTemplateBody} deliberately carries no money figures, only
 * package shape, so those three stay document-only, exactly like step one's {@code roleTitle}/
 * {@code location}.
 */
@Service
@Slf4j
public class PositionCompensationProposer {

    private static final String PROMPT_ID = "position-extract-compensation";
    private static final double EXTRACTION_TEMPERATURE = 0.0;
    private static final String ANSWER_MIME_TYPE = "application/json";
    private static final int EXTRACTION_THINKING_BUDGET = 0;

    /** Binds to {@link ModelCompensationAnswer}, whose only required field is {@code currency}. */
    private static final String BLOCKED = "{\"currency\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final Pattern CURRENCY_SHAPE = Pattern.compile("[A-Z]{3}");
    private static final int BONUS_VALUE_MAX_INTEGER_DIGITS = 4;
    private static final int BONUS_VALUE_MAX_FRACTION_DIGITS = 2;
    private static final int INCENTIVE_VESTING_MAX_LENGTH = 200;
    private static final int BENEFIT_NAME_MAX_LENGTH = 120;
    private static final int BENEFIT_MAX_COUNT = 20;

    /**
     * Matches a {@code "<name> — <frequency>"} value back apart at accept time. Anchored to the end of
     * the string and to the two literal tokens {@link #benefitFieldFrom} ever appends, so a benefit
     * name that happens to contain " — " in the middle is never mistaken for the appended suffix.
     */
    private static final Pattern BENEFIT_FREQUENCY_SUFFIX =
            Pattern.compile("^(.*) — (monthly|yearly)$", Pattern.CASE_INSENSITIVE);

    private static final String LABEL = "Compensation extraction";

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final PositionTemplateService templates;
    private final ExtractedFieldReader fieldReader;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    public PositionCompensationProposer(ChatClient chatClient,
                                        PositionDocumentRedactor redactor,
                                        PositionTemplateService templates,
                                        ExtractedFieldReader fieldReader,
                                        @Value("classpath:prompts/position-extract-compensation-system.st") Resource systemPrompt,
                                        @Value("classpath:prompts/position-extract-compensation-schema.json") Resource answerSchema,
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

    public ProposedCompensation propose(UUID userId, String documentText, UUID clientId, UUID workspaceId,
                                        String roleTitle) {
        llmBudget.require(LlmBudget.COMPENSATION_EXTRACT, userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelCompensationAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Compensation extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return finish(empty(), workspaceId, roleTitle);
            }
            return finish(reconcile(answered, redaction.pseudonyms(), documentText), workspaceId, roleTitle);
        } catch (RuntimeException e) {
            log.warn("Compensation extraction found nothing to propose", e);
            return finish(empty(), workspaceId, roleTitle);
        }
    }

    private ModelCompensationAnswer ask(String redactedText) {
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
                .entity(ModelCompensationAnswer.class);
    }

    private static boolean wasBlocked(ModelCompensationAnswer answered) {
        return BlockedAnswer.matches(answered.currency());
    }

    private static ProposedCompensation empty() {
        return new ProposedCompensation(ExtractionSource.NONE, List.of());
    }

    private ProposedCompensation reconcile(ModelCompensationAnswer answered, Pseudonyms pseudonyms,
                                           String originalText) {
        String haystack = fieldReader.haystackOf(originalText);
        List<ExtractedField> fields = new ArrayList<>();

        currencyFieldFrom(answered.currency(), answered.currencySnippet(), pseudonyms, haystack)
                .ifPresent(fields::add);
        longFieldFrom("salaryMin", answered.salaryMin(), answered.salaryMinSnippet(), pseudonyms, haystack)
                .ifPresent(fields::add);
        longFieldFrom("salaryMax", answered.salaryMax(), answered.salaryMaxSnippet(), pseudonyms, haystack)
                .ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "baseSalaryMode", BaseSalaryMode.class, answered.baseSalaryMode(),
                answered.baseSalaryModeSnippet(), pseudonyms, haystack).ifPresent(fields::add);
        bonusValueFieldFrom(answered.bonusValue(), answered.bonusValueSnippet(), pseudonyms, haystack)
                .ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "bonusBasis", BonusBasis.class, answered.bonusBasis(),
                answered.bonusBasisSnippet(), pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "incentiveType", IncentiveType.class, answered.incentiveType(),
                answered.incentiveTypeSnippet(), pseudonyms, haystack).ifPresent(fields::add);
        longFieldFrom("incentiveAmount", answered.incentiveAmount(), answered.incentiveAmountSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.fieldFrom(LABEL, "incentiveVesting", answered.incentiveVesting(), answered.incentiveVestingSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);

        if (answered.benefits() != null) {
            for (ModelBenefit benefit : answered.benefits()) {
                if (benefit == null) {
                    continue;
                }
                benefitFieldFrom(benefit, pseudonyms, haystack).ifPresent(fields::add);
            }
        }
        return new ProposedCompensation(ExtractionSource.MODEL, fields);
    }

    private Optional<ExtractedField> currencyFieldFrom(String rawValue, String rawSnippet,
                                                        Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, "currency", rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            String candidate = field.value().trim().toUpperCase(Locale.ROOT);
            return CURRENCY_SHAPE.matcher(candidate).matches()
                    ? Optional.of(new ExtractedField("currency", candidate, field.confidence(), field.snippet(),
                            field.origin()))
                    : Optional.empty();
        });
    }

    private Optional<ExtractedField> longFieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                                   Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, fieldKey, rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            try {
                long value = Long.parseLong(field.value().trim().replaceAll("[,\\s]", ""));
                return value < 0
                        ? Optional.empty()
                        : Optional.of(new ExtractedField(fieldKey, Long.toString(value), field.confidence(),
                                field.snippet(), field.origin()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Parsed as a {@code BigDecimal} and dropped — not truncated — unless it already fits
     * {@code numeric(6,2)}: truncating a figure states something the document did not.
     */
    private Optional<ExtractedField> bonusValueFieldFrom(String rawValue, String rawSnippet,
                                                         Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, "bonusValue", rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            BigDecimal value;
            try {
                value = new BigDecimal(field.value().trim().replaceAll("[,\\s]", ""));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (value.signum() < 0 || !fitsDigits(value)) {
                return Optional.empty();
            }
            // UNNECESSARY rather than HALF_UP: fitsDigits above already guarantees scale <= 2, so this
            // can never actually round anything — UNNECESSARY enforces that invariant instead of
            // silently rounding if it's ever violated.
            return Optional.of(new ExtractedField("bonusValue",
                    value.setScale(BONUS_VALUE_MAX_FRACTION_DIGITS, RoundingMode.UNNECESSARY).toPlainString(),
                    field.confidence(), field.snippet(), field.origin()));
        });
    }

    private static boolean fitsDigits(BigDecimal value) {
        // stripTrailingZeros can leave a negative scale for a whole number (120000 -> unscaled 12,
        // scale -4) — subtracting that negative scale is what correctly adds those trailing zeros
        // back into the integer digit count, so it is deliberately not clamped to zero here.
        BigDecimal normalised = value.stripTrailingZeros();
        return normalised.scale() <= BONUS_VALUE_MAX_FRACTION_DIGITS
                && normalised.unscaledValue().abs().toString().length() - normalised.scale()
                        <= BONUS_VALUE_MAX_INTEGER_DIGITS;
    }

    /**
     * One row per benefit, {@code value} the name alone or {@code "<name> — <frequency>"} when the
     * model's frequency token resolves — never the amount, which this proposer does not even ask for:
     * {@code BenefitDto.amount} is nullable and "not stated" is exactly the safe default.
     */
    private Optional<ExtractedField> benefitFieldFrom(ModelBenefit benefit, Pseudonyms pseudonyms,
                                                       String haystack) {
        return fieldReader.fieldFrom(LABEL, "benefit", benefit.name(), benefit.snippet(), pseudonyms, haystack)
                .map(field -> {
            BenefitFrequency frequency = benefit.frequency() == null
                    ? null
                    : fieldReader.enumFromName(BenefitFrequency.class, benefit.frequency());
            String value = frequency == null
                    ? field.value()
                    : field.value() + " — " + frequency.name().toLowerCase(Locale.ROOT);
            return new ExtractedField("benefit", value, field.confidence(), field.snippet(), field.origin());
        });
    }

    private ProposedCompensation finish(ProposedCompensation proposed, UUID workspaceId, String roleTitle) {
        List<ExtractedField> withTemplateBackfill = backfillFromTemplate(proposed.fields(), workspaceId, roleTitle);
        return new ProposedCompensation(proposed.source(), truncateToCeilings(withTemplateBackfill));
    }

    /**
     * Proposes the matched template's own package shape for a field neither the document nor the model
     * said anything about. Never {@code salaryMin}/{@code salaryMax}/{@code incentiveAmount}: the
     * template carries no money figures by design, only shape. Needs the mandate's own persisted role
     * title — unlike step one, this proposer never reads one out of the document itself.
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
        var body = matched.get().getBody();
        Set<String> present = fields.stream().map(ExtractedField::fieldKey).collect(Collectors.toSet());

        List<ExtractedField> backfilled = new ArrayList<>(fields);
        addIfMissing(backfilled, present, "currency", body.currency());
        addIfMissing(backfilled, present, "baseSalaryMode",
                body.baseSalaryMode() == null ? null : body.baseSalaryMode().name());
        addIfMissing(backfilled, present, "bonusValue",
                body.bonusValue() == null ? null : body.bonusValue().toPlainString());
        addIfMissing(backfilled, present, "bonusBasis",
                body.bonusBasis() == null ? null : body.bonusBasis().name());
        addIfMissing(backfilled, present, "incentiveType",
                body.incentiveType() == null ? null : body.incentiveType().name());
        addIfMissing(backfilled, present, "incentiveVesting", body.incentiveVesting());
        if (!present.contains("benefit")) {
            for (PositionTemplateBenefit benefit : body.benefits()) {
                if (benefit == null || benefit.name() == null || benefit.name().isBlank()) {
                    continue;
                }
                String value = benefit.frequency() == null
                        ? benefit.name()
                        : benefit.name() + " — " + benefit.frequency().name().toLowerCase(Locale.ROOT);
                backfilled.add(new ExtractedField("benefit", value, ProposalConfidence.LOW, null,
                        ProposalOrigin.TEMPLATE));
            }
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
     * Pre-truncates every value to {@code PutCompensationRequest}'s own ceilings, so accepting a
     * proposal can never 400 the autosave it is handed to. The numeric fields never reach here needing
     * truncation — they are dropped upstream instead — this only bounds the two free-text fields and
     * the benefit list. A benefit's name is capped before its frequency suffix is reattached, never
     * after: capping the combined string could slice a resolved "yearly" down to something that no
     * longer matches on accept, silently turning a yearly benefit into a monthly one.
     */
    private List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int benefitCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "incentiveVesting" -> truncated.add(fieldReader.capped(field, INCENTIVE_VESTING_MAX_LENGTH));
                case "benefit" -> {
                    if (benefitCount < BENEFIT_MAX_COUNT) {
                        truncated.add(cappedBenefit(field));
                        benefitCount++;
                    }
                }
                default -> truncated.add(field);
            }
        }
        return truncated;
    }

    private ExtractedField cappedBenefit(ExtractedField field) {
        Matcher suffix = BENEFIT_FREQUENCY_SUFFIX.matcher(field.value());
        if (!suffix.matches()) {
            return fieldReader.capped(field, BENEFIT_NAME_MAX_LENGTH);
        }
        String name = suffix.group(1);
        if (name.length() <= BENEFIT_NAME_MAX_LENGTH) {
            return field;
        }
        String value = name.substring(0, BENEFIT_NAME_MAX_LENGTH) + " — " + suffix.group(2);
        return new ExtractedField(field.fieldKey(), value, field.confidence(), field.snippet(), field.origin());
    }
}
