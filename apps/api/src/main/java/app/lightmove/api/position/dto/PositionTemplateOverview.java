package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.PositionDiscipline;
import app.lightmove.api.position.constant.PositionTemplateOrigin;
import java.time.Instant;
import java.util.List;

/** One row of a Templates list, in either scope. The drafted brief stays on {@link PositionTemplateDetail}. */
public record PositionTemplateOverview(
        String code,
        String title,
        PositionDiscipline discipline,
        Seniority seniority,
        String summary,
        List<String> keywords,

        /** Null in the library scope, where every row is the library. */
        PositionTemplateOrigin origin,

        boolean active,

        /** The template an unrecognised role title is drafted from; it can be neither archived nor hidden. */
        boolean fallback,

        boolean libraryChangedSinceCustomised,

        /** Library scope only: how many workspaces keep their own copy, and so will not see an edit. */
        Long customisedByWorkspaces,

        Instant revisedAt,

        /** Null where the reviser is not the caller's to name: a library template seen from a workspace. */
        String revisedByName
) {}
