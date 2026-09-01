package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.PositionDiscipline;
import app.lightmove.api.position.model.PositionTemplate;
import java.util.UUID;

/**
 * One option in the template picker. The content is deliberately absent: choosing a template applies
 * it and answers with the whole brief, so there is nothing for the screen to hold in the meantime and
 * no second copy of the brief's shape to keep in step with the first.
 *
 * <p>{@code shared} says where the template came from — the LightMove library, or this workspace's own
 * — which is the only distinction the picker has to draw before a firm can edit either.
 */
public record PositionTemplateSummary(
        UUID id,
        String code,
        String title,
        PositionDiscipline discipline,
        Seniority seniority,
        String summary,
        boolean shared
) {

    public static PositionTemplateSummary of(PositionTemplate template) {
        return new PositionTemplateSummary(template.getId(), template.getCode(), template.getTitle(),
                template.getDiscipline(), template.getSeniority(), template.getSummary(),
                template.isSharedLibrary());
    }
}
