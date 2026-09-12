package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.model.Pseudonyms;
import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.position.constant.BaseSalaryMode;
import app.lightmove.api.position.constant.BenefitFrequency;
import app.lightmove.api.position.constant.BonusBasis;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.IncentiveType;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ModelCompensationAnswer;
import app.lightmove.api.position.model.ModelCompensationAnswer.ModelBenefit;
import app.lightmove.api.position.model.ProposedCompensation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
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
 * <p><b>The headline guarantee of this proposer is that it comes back empty far more often than it
 * comes back with anything</b> — every numeric field is dropped rather than guessed at when the
 * document does not clearly state it, and nothing here ever estimates a market rate.
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

    private final ChatClient chatClient;
    private final PositionDocumentRedactor redactor;
    private final Resource systemPrompt;
    private final Consumer<ChatClient.AdvisorSpec> guarded;
    private final LlmBudgetGuard llmBudget;

    public PositionCompensationProposer(ChatClient chatClient,
                                        PositionDocumentRedactor redactor,
                                        @Value("classpath:prompts/position-extract-compensation-system.st") Resource systemPrompt,
                                        @Value("classpath:prompts/position-extract-compensation-schema.json") Resource answerSchema,
                                        LlmCallPolicy llmCalls,
                                        LlmBudgetGuard llmBudget) {
        this.chatClient = chatClient;
        this.redactor = redactor;
        this.systemPrompt = systemPrompt;
        this.guarded = llmCalls.forPrompt(PromptGuardSpec.structured(PROMPT_ID, answerSchema, BLOCKED));
        this.llmBudget = llmBudget;
    }

    public ProposedCompensation propose(UUID userId, String documentText, UUID clientId, UUID workspaceId) {
        llmBudget.requireCompensationExtractionBudget(userId);

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
            return finish(reconcile(answered, redaction.pseudonyms(), documentText));
        } catch (RuntimeException e) {
            log.warn("Compensation extraction found nothing to propose: {}", e.toString());
            return empty();
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
        List<ExtractedField> fields = new ArrayList<>();

        currencyFieldFrom(answered.currency(), answered.currencySnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        longFieldFrom("salaryMin", answered.salaryMin(), answered.salaryMinSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        longFieldFrom("salaryMax", answered.salaryMax(), answered.salaryMaxSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        enumFieldFrom("baseSalaryMode", BaseSalaryMode.class, answered.baseSalaryMode(),
                answered.baseSalaryModeSnippet(), pseudonyms, originalText).ifPresent(fields::add);
        bonusValueFieldFrom(answered.bonusValue(), answered.bonusValueSnippet(), pseudonyms, originalText)
                .ifPresent(fields::add);
        enumFieldFrom("bonusBasis", BonusBasis.class, answered.bonusBasis(),
                answered.bonusBasisSnippet(), pseudonyms, originalText).ifPresent(fields::add);
        enumFieldFrom("incentiveType", IncentiveType.class, answered.incentiveType(),
                answered.incentiveTypeSnippet(), pseudonyms, originalText).ifPresent(fields::add);
        longFieldFrom("incentiveAmount", answered.incentiveAmount(), answered.incentiveAmountSnippet(),
                pseudonyms, originalText).ifPresent(fields::add);
        fieldFrom("incentiveVesting", answered.incentiveVesting(), answered.incentiveVestingSnippet(),
                pseudonyms, originalText).ifPresent(fields::add);

        if (answered.benefits() != null) {
            for (ModelBenefit benefit : answered.benefits()) {
                if (benefit == null) {
                    continue;
                }
                benefitFieldFrom(benefit, pseudonyms, originalText).ifPresent(fields::add);
            }
        }
        return new ProposedCompensation(ExtractionSource.MODEL, fields);
    }

    /** Validated against {@code [A-Z]{3}} before the field is ever built — never after. */
    private Optional<ExtractedField> currencyFieldFrom(String rawValue, String rawSnippet,
                                                        Pseudonyms pseudonyms, String originalText) {
        return fieldFrom("currency", rawValue, rawSnippet, pseudonyms, originalText).flatMap(field -> {
            String candidate = field.value().trim().toUpperCase(Locale.ROOT);
            return CURRENCY_SHAPE.matcher(candidate).matches()
                    ? Optional.of(new ExtractedField("currency", candidate, field.confidence(), field.snippet()))
                    : Optional.empty();
        });
    }

    /** Parsed as a non-negative whole number; unparsable or negative is dropped, never truncated. */
    private Optional<ExtractedField> longFieldFrom(String fieldKey, String rawValue, String rawSnippet,
                                                   Pseudonyms pseudonyms, String originalText) {
        return fieldFrom(fieldKey, rawValue, rawSnippet, pseudonyms, originalText).flatMap(field -> {
            try {
                long value = Long.parseLong(field.value().trim().replaceAll("[,\\s]", ""));
                return value < 0
                        ? Optional.empty()
                        : Optional.of(new ExtractedField(fieldKey, Long.toString(value), field.confidence(),
                                field.snippet()));
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
                                                         Pseudonyms pseudonyms, String originalText) {
        return fieldFrom("bonusValue", rawValue, rawSnippet, pseudonyms, originalText).flatMap(field -> {
            BigDecimal value;
            try {
                value = new BigDecimal(field.value().trim().replaceAll("[,\\s]", ""));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (value.signum() < 0 || !fitsDigits(value)) {
                return Optional.empty();
            }
            return Optional.of(new ExtractedField("bonusValue",
                    value.setScale(BONUS_VALUE_MAX_FRACTION_DIGITS, RoundingMode.HALF_UP).toPlainString(),
                    field.confidence(), field.snippet()));
        });
    }

    private static boolean fitsDigits(BigDecimal value) {
        BigDecimal normalised = value.stripTrailingZeros();
        int fractionDigits = Math.max(0, normalised.scale());
        int integerDigits = normalised.precision() - normalised.scale();
        if (integerDigits < 0) {
            integerDigits = 1;
        }
        return fractionDigits <= BONUS_VALUE_MAX_FRACTION_DIGITS && integerDigits <= BONUS_VALUE_MAX_INTEGER_DIGITS;
    }

    /**
     * One row per benefit, {@code value} the name alone or {@code "<name> — <frequency>"} when the
     * model's frequency token resolves — never the amount, which this proposer does not even ask for:
     * {@code BenefitDto.amount} is nullable and "not stated" is exactly the safe default.
     */
    private Optional<ExtractedField> benefitFieldFrom(ModelBenefit benefit, Pseudonyms pseudonyms,
                                                       String originalText) {
        return fieldFrom("benefit", benefit.name(), benefit.snippet(), pseudonyms, originalText).map(field -> {
            BenefitFrequency frequency = benefit.frequency() == null
                    ? null
                    : enumFromName(BenefitFrequency.class, benefit.frequency());
            String value = frequency == null
                    ? field.value()
                    : field.value() + " — " + frequency.name().toLowerCase(Locale.ROOT);
            return new ExtractedField("benefit", value, field.confidence(), field.snippet());
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
            log.warn("Compensation extraction dropped a {} field: a placeholder survived re-hydration.",
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

    private static ProposedCompensation finish(ProposedCompensation proposed) {
        return new ProposedCompensation(proposed.source(), truncateToCeilings(proposed.fields()));
    }

    /**
     * Pre-truncates every value to {@code PutCompensationRequest}'s own ceilings, so accepting a
     * proposal can never 400 the autosave it is handed to. The numeric fields never reach here
     * needing truncation — they are dropped upstream instead — this only bounds the two free-text
     * fields and the benefit list.
     */
    private static List<ExtractedField> truncateToCeilings(List<ExtractedField> fields) {
        List<ExtractedField> truncated = new ArrayList<>();
        int benefitCount = 0;
        for (ExtractedField field : fields) {
            switch (field.fieldKey()) {
                case "incentiveVesting" -> truncated.add(capped(field, INCENTIVE_VESTING_MAX_LENGTH));
                case "benefit" -> {
                    if (benefitCount < BENEFIT_MAX_COUNT) {
                        truncated.add(capped(field, BENEFIT_NAME_MAX_LENGTH));
                        benefitCount++;
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
}
