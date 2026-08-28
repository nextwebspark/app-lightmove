package app.lightmove.api.position.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One seat in the org chart around the mandate.
 *
 * <p>Exactly one node per chart carries {@link #mandateSeat} — the role being searched for. Everything
 * the screen used to hold as separate fields reads off that flag: the manager is the mandate seat's
 * parent, the direct reports are its children, and a chart that wants a skip-level or a grandchild
 * adds a node rather than a concept.
 *
 * <p>Either half of a seat may be blank. A mandate knows "Group Treasurer" long before it knows who
 * sits there, and refusing the half it has would leave the chart unusable until the day it is complete.
 *
 * <p>{@code canvasX}/{@code canvasY} are where the box was dragged to, and are absent until it has
 * been — a chart nobody has arranged is laid out from the tree instead.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionOrgNode {

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    /** Null for a root — the top of the chart, which is usually the mandate's manager. */
    @Column(name = "parent_node_id")
    private UUID parentNodeId;

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "name", length = 160)
    private String name;

    @Column(name = "mandate_seat", nullable = false)
    private boolean mandateSeat;

    @Column(name = "canvas_x")
    private Float canvasX;

    @Column(name = "canvas_y")
    private Float canvasY;

    public static PositionOrgNode of(UUID nodeId, UUID parentNodeId, String title, String name,
                                     boolean mandateSeat, Float canvasX, Float canvasY) {
        PositionOrgNode node = new PositionOrgNode();
        node.nodeId = nodeId;
        node.parentNodeId = parentNodeId;
        node.title = trimmedOrNull(title);
        node.name = trimmedOrNull(name);
        node.mandateSeat = mandateSeat;
        node.canvasX = canvasX;
        node.canvasY = canvasY;
        return node;
    }

    /** The mandate's own seat is drawn from the role title, so it holds no title of its own. */
    public static PositionOrgNode mandateSeat(UUID nodeId, UUID parentNodeId) {
        return of(nodeId, parentNodeId, null, null, true, null, null);
    }

    /** True when neither half was filled in — a seat the chart has drawn but nobody has named. */
    public boolean isUnnamed() {
        return title == null && name == null;
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
