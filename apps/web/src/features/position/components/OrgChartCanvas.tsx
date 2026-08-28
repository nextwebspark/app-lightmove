import {
  Background,
  Controls,
  ReactFlow,
  type Connection,
  type Edge,
  type Node,
  type NodeChange,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useMemo } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { OrgNode } from "../api/types";
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  branchHoldsMandateSeat,
  childrenOf,
  layoutChart,
  removeBranch,
} from "../lib/orgChart";
import { OrgSeatNode, type OrgSeatData } from "./OrgSeatNode";

/**
 * The org chart, edited in place.
 *
 * A fresh brief opens on two tiers — the manager the template named, and the role's own seat beneath
 * it — with an add affordance under the seat, so the third tier is always one click away without a
 * placeholder box standing in for a report nobody has named. Every seat above and below can be edited,
 * moved, given children, or removed; only the mandate's own seat is fixed, because it is the mandate.
 *
 * Dragging a box stores where it was put. Anything never dragged is laid out from the tree, so
 * arranging one corner of a chart does not rearrange the rest.
 */
export function OrgChartCanvas({
  chart,
  roleTitle,
  onChange,
}: {
  chart: OrgNode[];
  roleTitle: string;
  /** `immediate` for structural edits, which are decisions rather than typing. */
  onChange: (chart: OrgNode[], immediate?: boolean) => void;
}) {
  const patch = useCallback(
    (nodeId: string, changes: Partial<OrgNode>, immediate = false) =>
      onChange(
        chart.map((node) => (node.nodeId === nodeId ? { ...node, ...changes } : node)),
        immediate,
      ),
    [chart, onChange],
  );

  const addChild = useCallback(
    (parentNodeId: string) =>
      onChange(
        [
          ...chart,
          {
            nodeId: crypto.randomUUID(),
            parentNodeId,
            title: null,
            name: null,
            mandateSeat: false,
            canvasX: null,
            canvasY: null,
          },
        ],
        true,
      ),
    [chart, onChange],
  );

  /** A new seat above a root becomes that root's parent, so the chart grows upward as one tree. */
  const addParent = useCallback(
    (childNodeId: string) => {
      const nodeId = crypto.randomUUID();
      onChange(
        [
          ...chart.map((node) =>
            node.nodeId === childNodeId ? { ...node, parentNodeId: nodeId } : node,
          ),
          {
            nodeId,
            parentNodeId: null,
            title: null,
            name: null,
            mandateSeat: false,
            canvasX: null,
            canvasY: null,
          },
        ],
        true,
      );
    },
    [chart, onChange],
  );

  const remove = useCallback(
    (nodeId: string) => onChange(removeBranch(chart, nodeId), true),
    [chart, onChange],
  );

  const layout = useMemo(() => layoutChart(chart), [chart]);

  const nodes: Node<OrgSeatData>[] = useMemo(
    () =>
      chart.map((seat) => {
        const fallback = layout.get(seat.nodeId) ?? { x: 0, y: 0 };
        return {
          id: seat.nodeId,
          type: "orgSeat",
          position: {
            x: seat.canvasX ?? fallback.x,
            y: seat.canvasY ?? fallback.y,
          },
          data: {
            seat,
            roleTitle,
            childCount: childrenOf(chart, seat.nodeId).length,
            isRoot: seat.parentNodeId === null,
            canRemove: !seat.mandateSeat && !branchHoldsMandateSeat(chart, seat.nodeId),
            onPatch: patch,
            onAddChild: addChild,
            onAddParent: addParent,
            onRemove: remove,
          },
        };
      }),
    [chart, layout, roleTitle, patch, addChild, addParent, remove],
  );

  const edges: Edge[] = useMemo(
    () =>
      chart
        .filter((seat) => seat.parentNodeId !== null)
        .map((seat) => ({
          id: `${seat.parentNodeId}-${seat.nodeId}`,
          source: seat.parentNodeId as string,
          target: seat.nodeId,
          type: "smoothstep",
        })),
    [chart],
  );

  const handleNodesChange = useCallback(
    (changes: NodeChange<Node<OrgSeatData>>[]) => {
      // Only the end of a drag is persisted: saving every intermediate frame would fire a write per
      // animation tick, and the position mid-drag is not a decision anyone has made yet.
      const moved = changes.filter(
        (change) => change.type === "position" && change.dragging === false && change.position,
      );
      if (moved.length === 0) return;
      onChange(
        chart.map((seat) => {
          const move = moved.find((change) => "id" in change && change.id === seat.nodeId);
          return move && move.type === "position" && move.position
            ? { ...seat, canvasX: move.position.x, canvasY: move.position.y }
            : seat;
        }),
        true,
      );
    },
    [chart, onChange],
  );

  /** Dragging one seat onto another re-parents it — the chart is a tree, so a seat has one manager. */
  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;
      if (connection.source === connection.target) return;
      if (branchHoldsMandateSeat(chart, connection.target)) return;
      // Re-parenting a seat under its own descendant would make a loop the chart cannot draw.
      if (!removeBranch(chart, connection.target).some((node) => node.nodeId === connection.source)) {
        return;
      }
      patch(connection.target, { parentNodeId: connection.source }, true);
    },
    [chart, patch],
  );

  return (
    <div className="h-[380px] overflow-hidden rounded-[10px] border border-line-soft bg-panel2">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        onNodesChange={handleNodesChange}
        onConnect={handleConnect}
        fitView
        fitViewOptions={FIT_VIEW}
        minZoom={0.3}
        maxZoom={1.5}
        proOptions={PRO_OPTIONS}
        aria-label="Org chart"
        className="[&_.react-flow__attribution]:hidden"
      >
        <Background gap={18} size={1} className="text-line" />
        <Controls showInteractive={false} className="!shadow-none" />
      </ReactFlow>
    </div>
  );
}

const NODE_TYPES = { orgSeat: OrgSeatNode };
const FIT_VIEW = { padding: 0.2, maxZoom: 1 };
/** The React Flow watermark is a paid-plan removal; hiding it is what the flag is for. */
const PRO_OPTIONS = { hideAttribution: true };

/** Exported for the seat node, which draws the same add affordance for children and for parents. */
export function SeatActionButton({
  label,
  icon,
  tone = "sky",
  onClick,
}: {
  label: string;
  icon: string;
  tone?: "sky" | "red";
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        "grid size-5 place-items-center rounded-full border bg-panel transition",
        tone === "red"
          ? "border-line text-text3 hover:border-red hover:text-red"
          : "border-line text-text3 hover:border-sky hover:text-sky",
      )}
    >
      <Icon d={icon} size={11} />
    </button>
  );
}

export const SEAT_ICONS = { add: ICONS.plus, remove: ICONS.close };
export const SEAT_SIZE = { width: NODE_WIDTH, height: NODE_HEIGHT };
