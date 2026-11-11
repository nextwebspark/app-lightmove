package app.lightmove.api.report.dto;

import app.lightmove.api.report.model.RankingScenario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * The scenario as the screen holds it. {@code version} is null while none is saved; a scenario equal to the brief's
 * weighting clears the saved one.
 *
 * @param weights competency key → weight, every competency of the brief
 */
public record SaveRankingScenarioRequest(
        Long version,
        @Min(0) @Max(100) int technicalShare,
        @NotNull @Size(max = 20) Map<String, @NotNull @Min(0) @Max(100) Integer> weights,
        @Size(max = RankingScenario.MAX_RATIONALE) String rationale
) {

    public String rationaleOrNull() {
        return rationale == null || rationale.isBlank() ? null : rationale.strip();
    }
}
