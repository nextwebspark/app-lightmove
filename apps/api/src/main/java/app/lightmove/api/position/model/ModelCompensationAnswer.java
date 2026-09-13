package app.lightmove.api.position.model;

import java.util.List;

/**
 * What the model answered when asked to read a position description for step four — raw and
 * unchecked. See {@link ModelDetailsAnswer} for why this is named for where it came from rather than
 * for what it proposes; turning it into {@link ExtractedField} rows happens in
 * {@link app.lightmove.api.position.service.PositionCompensationProposer}.
 *
 * <p>Every numeric field is a plain string, exactly like every other field on this record: leaving a
 * figure null is the model's easy answer, and a junk token ("150%") never fails JSON binding — it is
 * parsed and validated in the proposer, which drops what does not fit rather than guessing at it.
 */
public record ModelCompensationAnswer(
        String currency,
        String currencySnippet,
        String salaryMin,
        String salaryMinSnippet,
        String salaryMax,
        String salaryMaxSnippet,
        String baseSalaryMode,
        String baseSalaryModeSnippet,
        String bonusValue,
        String bonusValueSnippet,
        String bonusBasis,
        String bonusBasisSnippet,
        String incentiveType,
        String incentiveTypeSnippet,
        String incentiveAmount,
        String incentiveAmountSnippet,
        String incentiveVesting,
        String incentiveVestingSnippet,
        List<ModelBenefit> benefits
) {

    /** One benefit or allowance, its stated frequency if any, and the sentence it was read from. */
    public record ModelBenefit(String name, String frequency, String snippet) {}
}
