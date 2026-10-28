package app.lightmove.api.position.model;

import java.util.List;

/**
 * What the model answered when asked to read a position description for step four — raw and
 * unchecked. Every figure is a plain string, so a junk token ("150%") never fails binding; the proposer
 * parses each one and drops what does not fit rather than guessing at it.
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

    /** One allowance or benefit, its stated amount and frequency if any, and the sentence it was read from. */
    public record ModelBenefit(String name, String amount, String frequency, String snippet) {}
}
