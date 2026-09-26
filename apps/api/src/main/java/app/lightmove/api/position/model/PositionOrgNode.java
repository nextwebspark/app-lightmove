package app.lightmove.api.position.model;

import app.lightmove.api.core.text.service.SuppliedText;
import app.lightmove.api.position.constant.FieldSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One seat in the org chart around the mandate; exactly one per chart carries {@link #mandateSeat}.
 * Canvas coordinates are absent until the box has been dragged.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionOrgNode {

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    public static PositionOrgNode of(UUID nodeId, UUID parentNodeId, String title, String name,
                                     boolean mandateSeat, Float canvasX, Float canvasY,
                                     FieldSource source) {
        PositionOrgNode node = new PositionOrgNode();
        node.nodeId = nodeId;
        node.parentNodeId = parentNodeId;
        node.title = SuppliedText.blankToNull(title);
        node.name = SuppliedText.blankToNull(name);
        node.mandateSeat = mandateSeat;
        node.canvasX = canvasX;
        node.canvasY = canvasY;
        node.source = source;
        return node;
    }

    /** The mandate's own seat is drawn from the role title, so it holds no title of its own. */
    public static PositionOrgNode mandateSeat(UUID nodeId, UUID parentNodeId, FieldSource source) {
        return of(nodeId, parentNodeId, null, null, true, null, null, source);
    }
}
