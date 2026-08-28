import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { cn } from "../../../lib/cn";
import type { OrgNode } from "../api/types";
import { NODE_HEIGHT, NODE_WIDTH } from "../lib/orgChart";
import { SEAT_ICONS, SeatActionButton } from "./OrgChartCanvas";

export interface OrgSeatData extends Record<string, unknown> {
  seat: OrgNode;
  /** The mandate's own seat is drawn from step one's role title rather than holding its own. */
  roleTitle: string;
  childCount: number;
  isRoot: boolean;
  canRemove: boolean;
  onPatch: (nodeId: string, changes: Partial<OrgNode>, immediate?: boolean) => void;
  onAddChild: (parentNodeId: string) => void;
  onAddParent: (childNodeId: string) => void;
  onRemove: (nodeId: string) => void;
}

/**
 * One box on the canvas: a title, a name, and the handles that connect it.
 *
 * Edited in place rather than through a side panel — the whole point of the canvas is that the chart
 * is the form. The mandate's own seat shows the role title from step one and cannot be renamed here
 * or removed, because it is the mandate rather than a seat somebody drew.
 */
export function OrgSeatNode({ data }: NodeProps<Node<OrgSeatData>>) {
  const { seat, roleTitle, childCount, isRoot, canRemove } = data;
  const isMandate = seat.mandateSeat;

  return (
    <div
      style={{ width: NODE_WIDTH, minHeight: NODE_HEIGHT }}
      className={cn(
        "group relative rounded-lg border px-3 py-2 shadow-sm transition",
        isMandate ? "border-sky bg-sky-dim" : "border-line bg-panel hover:border-text3",
      )}
    >
      <Handle type="target" position={Position.Top} className="!size-2 !border-line !bg-panel2" />

      {isMandate ? (
        <>
          <span className="block truncate text-[13px] font-semibold text-sky">
            {roleTitle.trim() || "Untitled role"}
          </span>
          <span className="mt-px block font-mono text-[10.5px] uppercase tracking-[0.06em] text-text3">
            This position
          </span>
        </>
      ) : (
        <>
          <input
            value={seat.title ?? ""}
            aria-label="Seat title"
            placeholder="Title"
            onChange={(event) => data.onPatch(seat.nodeId, { title: event.target.value || null })}
            className="w-full bg-transparent text-[13px] font-semibold text-text outline-none placeholder:font-normal placeholder:text-text3"
          />
          <input
            value={seat.name ?? ""}
            aria-label="Seat holder name"
            placeholder="Name"
            onChange={(event) => data.onPatch(seat.nodeId, { name: event.target.value || null })}
            className="w-full bg-transparent font-mono text-[11px] text-text3 outline-none placeholder:text-text3/60"
          />
        </>
      )}

      {/* The controls stay out of the way until the seat is pointed at — a canvas of boxes wearing
          three buttons each reads as a toolbar rather than a chart. Focus-within keeps them reachable
          from the keyboard, where there is no hover to rely on. */}
      <span className="absolute -top-2.5 end-2 flex gap-1 opacity-0 transition group-focus-within:opacity-100 group-hover:opacity-100">
        {isRoot && (
          <SeatActionButton
            label="Add a manager above"
            icon={SEAT_ICONS.add}
            onClick={() => data.onAddParent(seat.nodeId)}
          />
        )}
        {canRemove && (
          <SeatActionButton
            label="Remove this seat"
            icon={SEAT_ICONS.remove}
            tone="red"
            onClick={() => data.onRemove(seat.nodeId)}
          />
        )}
      </span>

      <span className="absolute -bottom-2.5 left-1/2 -translate-x-1/2 opacity-0 transition group-focus-within:opacity-100 group-hover:opacity-100">
        <SeatActionButton
          label={isMandate ? "Add a direct report" : "Add a seat below"}
          icon={SEAT_ICONS.add}
          onClick={() => data.onAddChild(seat.nodeId)}
        />
      </span>

      {childCount > 0 && (
        <span className="sr-only">
          {childCount} seat{childCount === 1 ? "" : "s"} report here
        </span>
      )}

      <Handle type="source" position={Position.Bottom} className="!size-2 !border-line !bg-panel2" />
    </div>
  );
}
