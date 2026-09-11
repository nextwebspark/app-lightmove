package app.lightmove.api.position.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One seat in the org chart — the same shape reads and writes.
 *
 * <p>Ids are the client's: the chart is written whole, so the screen names its own nodes rather than
 * reconciling server-assigned ids mid-edit. Both name fields are optional, because a mandate knows
 * the seat long before the person.
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
