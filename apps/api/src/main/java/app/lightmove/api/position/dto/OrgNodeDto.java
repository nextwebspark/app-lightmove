package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.FieldSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** One seat in the org chart, read and written; ids are the client's, since the chart is written whole. */
public record OrgNodeDto(
        @NotNull(message = "Every seat needs an id")
        UUID nodeId,

        UUID parentNodeId,

        @Size(max = 160, message = "That title is too long") String title,
        @Size(max = 160, message = "That name is too long") String name,

        boolean mandateSeat,

        /** Absent until the box has been dragged. */
        Float canvasX,
        Float canvasY,

        /** Null on a write defaults to {@code MANUAL}. */
        FieldSource source
) {}
