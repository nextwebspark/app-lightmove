import {
  Background,
  Controls,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  type Connection,
  type Edge,
  type Node,
  type NodeChange,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useMemo, useRef } from "react";
import type { OrgNode } from "../api/types";
import {
  branchHoldsMandateSeat,
  childrenOf,
  layoutChart,
  MAX_ORG_CHART_SEATS,
  removeBranch,
  removeSeat,
} from "../lib/orgChart";
import { OrgSeatNode, type OrgSeatData } from "./OrgSeatNode";

/**
 * The org chart, edited in place. Every seat but the mandate's own can be edited, moved or removed; removing one lifts
 * its reports onto the seat above. A dragged box keeps its place; anything never dragged is laid out from the tree.
 */
export function OrgChartCanvas(props: OrgChartCanvasProps) {
  // The provider is what gives the canvas below access to the viewport — see useSeatInView.
  return (
    <ReactFlowProvider>
      <OrgChart {...props} />
    </ReactFlowProvider>
  );
}

interface OrgChartCanvasProps {
  chart: OrgNode[];
  roleTitle: string;
  /** `immediate` for structural edits, which are decisions rather than typing. */
  onChange: (chart: OrgNode[], immediate?: boolean) => void;
}

function OrgChart({ chart, roleTitle, onChange }: OrgChartCanvasProps) {
  useSeatInView(chart.length);
  const patch = useCallback(
    (nodeId: string, changes: Partial<OrgNode>, immediate = false) =>
      onChange(
        chart.map((node) =>
          node.nodeId === nodeId ? { ...node, ...changes, source: "MANUAL" } : node,
        ),
        immediate,
      ),
    [chart, onChange],
  );

  const addChild = useCallback(
    (parentNodeId: string) => {
      if (chart.length >= MAX_ORG_CHART_SEATS) return;
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
            source: "MANUAL",
          },
        ],
        true,
      );
    },
    [chart, onChange],
  );

  /** A new seat above a root becomes that root's parent, so the chart grows upward as one tree. */
  const addParent = useCallback(
    (childNodeId: string) => {
      if (chart.length >= MAX_ORG_CHART_SEATS) return;
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
            source: "MANUAL",
          },
        ],
        true,
      );
    },
    [chart, onChange],
  );

  const remove = useCallback(
    (nodeId: string) => onChange(removeSeat(chart, nodeId), true),
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
            canRemove: !seat.mandateSeat,
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
    <div className="h-[420px] overflow-hidden rounded-[11px] bg-u-surface shadow-u-e1">
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
        ariaLabelConfig={ARIA_LABELS}
        aria-label="Org chart"
        className="[&_.react-flow__attribution]:hidden"
      >
        <Background gap={18} size={1} className="text-u-border-strong" />
        <Controls
          position="bottom-left"
          showInteractive={false}
          className="!rounded-[8px] !border !border-u-border !bg-u-bg !shadow-u-e1 [&>button]:!border-u-border [&>button]:!bg-u-bg [&>button]:!fill-u-text2 [&>button:hover]:!bg-u-raised"
        />
      </ReactFlow>
    </div>
  );
}

/**
 * Brings a newly added seat into view.
 *
 * A chart wider than its frame puts the next seat outside it, and a button that appears to do nothing
 * is worse than one that moves the view. Only a growing chart refits — dragging a box around must not
 * rescale everything underneath the hand doing the dragging.
 */
function useSeatInView(seatCount: number) {
  const { fitView } = useReactFlow();
  const previousCount = useRef(seatCount);

  useEffect(() => {
    if (seatCount > previousCount.current) {
      void fitView({ padding: 0.2, maxZoom: 1, duration: 200 });
    }
    previousCount.current = seatCount;
  }, [seatCount, fitView]);
}

const NODE_TYPES = { orgSeat: OrgSeatNode };
const FIT_VIEW = { padding: 0.2, maxZoom: 1 };
/** The React Flow watermark is a paid-plan removal; hiding it is what the flag is for. */
const PRO_OPTIONS = { hideAttribution: true };

const ARIA_LABELS = {
  "controls.zoomIn.ariaLabel": "Zoom in",
  "controls.zoomOut.ariaLabel": "Zoom out",
  "controls.fitView.ariaLabel": "Fit view",
};
