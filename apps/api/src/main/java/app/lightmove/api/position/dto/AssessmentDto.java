package app.lightmove.api.position.dto;

import java.util.List;

/**
 * Step five as the brief returns it. The two competency panels arrive split even though they are one
 * ordered list in storage, because the screen draws them as two; {@code technicalShare} is how much
 * of the assessment the technical panel carries, the behavioural panel carrying the rest.
 */
public record AssessmentDto(
        List<CriterionResponse> criteria,
        List<CompetencyDto> technical,
        List<CompetencyDto> behavioural,
        int technicalShare
) {}
