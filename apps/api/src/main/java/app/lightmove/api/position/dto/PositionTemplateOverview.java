package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.PositionDiscipline;
import app.lightmove.api.position.constant.PositionTemplateOrigin;

/** One row of a Templates list, in either scope. The content stays on {@link PositionTemplateDetail}. */
public record PositionTemplateOverview(
        String code,
        String title,
        PositionDiscipline discipline,
        Seniority seniority,
        String summary,

        /** Null in the library scope, where every row is the library. */
        PositionTemplateOrigin origin,

        boolean active,

        /** The template an unrecognised role title is drafted from; it can be neither archived nor hidden. */
        boolean fallback,

        boolean libraryChangedSinceCustomised
) {}
