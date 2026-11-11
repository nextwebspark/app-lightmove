package app.lightmove.api.positiontemplate.dto;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import java.util.UUID;

/**
 * One template picker option, content absent (applying one answers the whole brief). {@code shared}: the library's or
 * this workspace's own.
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
