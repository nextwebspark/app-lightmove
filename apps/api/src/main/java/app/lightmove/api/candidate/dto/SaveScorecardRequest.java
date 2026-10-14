package app.lightmove.api.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The whole assessment, replaced on every save.
 *
 * @param version      the version the editor opened, null for the first save — a stale one is refused
 * @param markReviewed Mark reviewed &amp; submit; false saves a draft, and any save of a reviewed one is a draft again
 */
public record SaveScorecardRequest(
        Long version,
        @NotNull @Size(max = 40) List<@Valid @NotNull CriterionScoreRequest> scores,
        @NotNull @Valid ScorecardReportDto report,
        boolean markReviewed
) {}
