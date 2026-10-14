package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.CandidateScorecard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One criterion's score as sent: the brief's panel and name it was given against, matched on save. */
public record CriterionScoreRequest(
        /** {@code technical} or {@code behavioural} — {@link app.lightmove.api.common.constant.CompetencyPanel}'s tokens. */
        @NotBlank
        String panel,
        @NotBlank @Size(max = 120)
        String name,
        @Min(value = CandidateScorecard.MIN_SCORE, message = "Scores are between 0 and 10")
        @Max(value = CandidateScorecard.MAX_SCORE, message = "Scores are between 0 and 10")
        Integer score,
        @Size(max = CandidateScorecard.MAX_EVIDENCE, message = "Keep the evidence under 2,000 characters")
        String evidence
) {}
