package app.lightmove.api.position.dto;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.PositionDiscipline;
import app.lightmove.api.position.constant.PositionTemplateOrigin;
import app.lightmove.api.position.model.PositionTemplateBody;
import java.time.Instant;
import java.util.List;

/** One template as its editor opens it. */
public record PositionTemplateDetail(
        String code,
        String title,
        PositionDiscipline discipline,
        Seniority seniority,
        String summary,
        List<String> keywords,
        PositionTemplateBody body,

        /** Null in the library scope. */
        PositionTemplateOrigin origin,

        boolean active,
        boolean fallback,
        boolean libraryChangedSinceCustomised,

        /** Library scope only: how many workspaces keep their own copy, and so will not see an edit. */
        Long customisedByWorkspaces,

        /** The token a save sends back. For a library template opened from a workspace, the library row's. */
        long version,

        Instant revisedAt,

        /** Null where the reviser is not the caller's to name: a library template seen from a workspace. */
        String revisedByName
) {}
