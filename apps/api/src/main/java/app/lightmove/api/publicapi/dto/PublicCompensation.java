package app.lightmove.api.publicapi.dto;

import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Compensation", description = "An executive's current package, as recorded; each figure annual")
public record PublicCompensation(
        @Schema(description = "ISO 4217 currency of every figure", nullable = true, example = "AED") String currency,
        @Schema(description = "Base salary", nullable = true, example = "1200000") Long baseSalary,
        @Schema(description = "Bonus", nullable = true, example = "300000") Long bonus,
        @Schema(description = "Allowances, summed", nullable = true, example = "240000") Long allowances,
        @Schema(description = "Long-term incentive", nullable = true, example = "500000") Long longTermIncentive,
        @Schema(description = "Notice period", nullable = true, example = "3 months") String noticePeriod
) {

    public static PublicCompensation of(CandidateCompensationDto compensation) {
        return new PublicCompensation(compensation.currency(), compensation.baseSalary(), compensation.bonus(),
                compensation.allowances(), compensation.longTermIncentive(), compensation.noticePeriod());
    }
}
