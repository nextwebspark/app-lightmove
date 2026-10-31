package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelCompensationAnswer.ModelBenefit;
import app.lightmove.api.position.model.ModelCompensationAnswer;
import app.lightmove.api.position.model.ProposedCompensation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads a position description into step-four proposals, redacted as {@link PositionDetailsProposer}
 * does. No heuristic backs it up and nothing is estimated: a figure the document does not state stays
 * unproposed, and a failure answers {@link ExtractionSource#NONE}.
 */
@Service
@Slf4j
public class PositionCompensationProposer {

    private static final String PROMPT_ID = "position-extract-compensation";
    private static final String LABEL = "Compensation extraction";

    /** Binds to {@link ModelCompensationAnswer}, whose only required field is {@code currency}. */
    private static final String BLOCKED = "{\"currency\":\"" + BlockedAnswer.MARKER + "\"}";

    private static final Pattern CURRENCY_SHAPE = Pattern.compile("[A-Z]{3}");
    private static final Pattern FIGURE_SEPARATORS = Pattern.compile("[,\\s]");

    /** {@code PutCompensationRequest.bonusValue}'s {@code @Digits(integer = 12, fraction = 2)}. */
    private static final int BONUS_VALUE_MAX_INTEGER_DIGITS = 12;
    private static final int BONUS_VALUE_MAX_FRACTION_DIGITS = 2;
    private static final int INCENTIVE_VESTING_MAX_LENGTH = 200;
    private static final int BENEFIT_NAME_MAX_LENGTH = 120;
    private static final int BENEFIT_MAX_COUNT = 20;

    /**
     * Packs {@code "<name>[ — <monthly|yearly|unstated>[ — <amount>]]"}; the frontend reads segment 2 as the
     * frequency and segment 3 as the amount, so a name carrying the separator is dropped.
     */
    private static final String PACK_SEPARATOR = " — ";
    private static final String UNSTATED_FREQUENCY = "unstated";

    private final PositionDocumentRedactor redactor;
    private final ExtractedFieldReader fieldReader;
    private final StructuredPrompt prompt;
    private final LlmBudgetGuard llmBudget;

    public PositionCompensationProposer(StructuredPromptFactory prompts,
                                        PositionDocumentRedactor redactor,
                                        ExtractedFieldReader fieldReader,
                                        LlmBudgetGuard llmBudget) {
        this.redactor = redactor;
        this.fieldReader = fieldReader;
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
        this.llmBudget = llmBudget;
    }

    public ProposedCompensation propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        llmBudget.require(LlmBudget.COMPENSATION_EXTRACT, userId);

        try {
            Redaction redaction = redactor.redact(documentText, clientId, workspaceId);
            ModelCompensationAnswer answered = ask(redaction.text());
            if (answered == null || wasBlocked(answered)) {
                if (answered != null) {
                    log.warn("Compensation extraction blocked before reaching the model: the document "
                            + "matched the injection word list.");
                }
                return empty();
            }
            return reconcile(answered, redaction.pseudonyms(), documentText);
        } catch (RuntimeException e) {
            log.warn("Compensation extraction found nothing to propose", e);
            return empty();
        }
    }

    private ModelCompensationAnswer ask(String redactedText) {
        return prompt.ask(ModelCompensationAnswer.class, user -> user.text("""
                Position description text:
                {text}
                """)
                .param("text", redactedText));
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
        wholeFigureFieldFrom("salaryMin", answered.salaryMin(), answered.salaryMinSnippet(), pseudonyms, haystack)
                .ifPresent(fields::add);
        wholeFigureFieldFrom("salaryMax", answered.salaryMax(), answered.salaryMaxSnippet(), pseudonyms, haystack)
                .ifPresent(fields::add);
        fieldReader.enumFieldFrom(LABEL, "baseSalaryMode", BaseSalaryMode.class, answered.baseSalaryMode(),
                answered.baseSalaryModeSnippet(), pseudonyms, haystack).ifPresent(fields::add);

        Optional<ExtractedField> bonusBasis = fieldReader.enumFieldFrom(LABEL, "bonusBasis", BonusBasis.class,
                answered.bonusBasis(), answered.bonusBasisSnippet(), pseudonyms, haystack);
        bonusValueFieldFrom(answered.bonusValue(), answered.bonusValueSnippet(), pseudonyms, haystack,
                bonusBasis.map(field -> BonusBasis.valueOf(field.value())).orElse(null))
                .ifPresent(fields::add);
        bonusBasis.ifPresent(fields::add);

        fieldReader.enumFieldFrom(LABEL, "incentiveType", IncentiveType.class, answered.incentiveType(),
                answered.incentiveTypeSnippet(), pseudonyms, haystack).ifPresent(fields::add);
        wholeFigureFieldFrom("incentiveAmount", answered.incentiveAmount(), answered.incentiveAmountSnippet(),
                pseudonyms, haystack).ifPresent(fields::add);
        fieldReader.fieldFrom(LABEL, "incentiveVesting", answered.incentiveVesting(),
                        answered.incentiveVestingSnippet(), pseudonyms, haystack)
                .map(field -> fieldReader.capped(field, INCENTIVE_VESTING_MAX_LENGTH))
                .ifPresent(fields::add);

        if (answered.benefits() != null) {
            answered.benefits().stream()
                    .filter(benefit -> benefit != null)
                    .map(benefit -> benefitFieldFrom(benefit, pseudonyms, haystack))
                    .flatMap(Optional::stream)
                    .limit(BENEFIT_MAX_COUNT)
                    .forEach(fields::add);
        }
        return new ProposedCompensation(ExtractionSource.MODEL, fields);
    }

    private Optional<ExtractedField> currencyFieldFrom(String rawValue, String rawSnippet,
                                                        Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, "currency", rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            String code = field.value().trim().toUpperCase(Locale.ROOT);
            return CURRENCY_SHAPE.matcher(code).matches()
                    ? Optional.of(field.withValue(code))
                    : Optional.empty();
        });
    }

    private Optional<ExtractedField> wholeFigureFieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                                          Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, fieldKey, rawValue, rawSnippet, pseudonyms, haystack)
                .flatMap(field -> wholeFigureOf(field.value()).map(figure -> field.withValue(Long.toString(figure))));
    }

    private static Optional<Long> wholeFigureOf(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            long figure = Long.parseLong(FIGURE_SEPARATORS.matcher(raw.trim()).replaceAll(""));
            return figure < 0 ? Optional.empty() : Optional.of(figure);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Dropped, never truncated or clamped, when it will not fit the column or its basis's ceiling: a
     * cut figure states something the document did not, and one past the ceiling would 400 the autosave.
     */
    private Optional<ExtractedField> bonusValueFieldFrom(String rawValue, String rawSnippet, Pseudonyms pseudonyms,
                                                         String haystack, BonusBasis basis) {
        return fieldReader.fieldFrom(LABEL, "bonusValue", rawValue, rawSnippet, pseudonyms, haystack).flatMap(field -> {
            BigDecimal value;
            try {
                value = new BigDecimal(FIGURE_SEPARATORS.matcher(field.value().trim()).replaceAll("").replace("%", ""));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            BigDecimal normalised = value.stripTrailingZeros();
            boolean fits = value.signum() >= 0
                    && normalised.scale() <= BONUS_VALUE_MAX_FRACTION_DIGITS
                    && normalised.precision() - normalised.scale() <= BONUS_VALUE_MAX_INTEGER_DIGITS;
            if (!fits || (basis != null && !basis.admits(value))) {
                return Optional.empty();
            }
            return Optional.of(field.withValue(normalised.scale() < 0
                    ? normalised.setScale(0).toPlainString()
                    : normalised.toPlainString()));
        });
    }

    private Optional<ExtractedField> benefitFieldFrom(ModelBenefit benefit, Pseudonyms pseudonyms, String haystack) {
        return fieldReader.fieldFrom(LABEL, "benefit", benefit.name(), benefit.snippet(), pseudonyms, haystack)
                .flatMap(field -> {
                    if (field.value().contains(PACK_SEPARATOR)) {
                        log.warn("Compensation extraction dropped a benefit: its name contained the packing separator.");
                        return Optional.empty();
                    }
                    String name = field.value().length() > BENEFIT_NAME_MAX_LENGTH
                            ? field.value().substring(0, BENEFIT_NAME_MAX_LENGTH)
                            : field.value();
                    BenefitFrequency frequency = benefit.frequency() == null
                            ? null
                            : fieldReader.enumFromName(BenefitFrequency.class, benefit.frequency());
                    Long amount = wholeFigureOf(benefit.amount()).orElse(null);
                    return Optional.of(field.withValue(pack(name, frequency, amount)));
                });
    }

    private static String pack(String name, BenefitFrequency frequency, Long amount) {
        if (frequency == null && amount == null) {
            return name;
        }
        String frequencyToken = frequency == null ? UNSTATED_FREQUENCY : frequency.name().toLowerCase(Locale.ROOT);
        String packed = name + PACK_SEPARATOR + frequencyToken;
        return amount == null ? packed : packed + PACK_SEPARATOR + amount;
    }
}
