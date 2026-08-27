package app.lightmove.api.position.dto;

import java.util.List;

/**
 * Step five as the brief returns it. The two competency panels arrive split even though they are one
 * ordered list in storage, because the screen draws them as two.
 */
public record AssessmentDto(
        List<CriterionResponse> criteria,
        List<CompetencyDto> technical,
        List<CompetencyDto> behavioural
) {}
