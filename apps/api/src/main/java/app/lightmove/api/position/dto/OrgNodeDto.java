package app.lightmove.api.position.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One seat in the org chart — the same shape reads and writes.
 *
 * <p>Ids are the client's: the chart is written whole, so the screen names its own nodes and the
 * server stores the references it was given rather than handing back ids the canvas would then have
 * to reconcile mid-edit.
 *
 * <p>Both name fields are optional. A mandate knows the seat long before the person, and a chart that
 * refused half an answer would be unusable until the day it was complete.
 */
public record OrgNodeDto(
        @NotNull(message = "Every seat needs an id")
        UUID nodeId,

        /** Null for a root — the top of the chart, usually the mandate's manager. */
        UUID parentNodeId,

        @Size(max = 160, message = "That title is too long") String title,
        @Size(max = 160, message = "That name is too long") String name,

        /** True on exactly one seat: the role this brief is for. */
        boolean mandateSeat,

        /** Where the box was dragged to. Absent until it has been, and then laid out from the tree. */
        Float canvasX,
        Float canvasY
) {}
