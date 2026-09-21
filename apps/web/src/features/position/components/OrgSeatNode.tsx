import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { OrgNode } from "../api/types";
import { NODE_HEIGHT, NODE_WIDTH } from "../lib/orgChart";

export interface OrgSeatData extends Record<string, unknown> {
  seat: OrgNode;
  /** The mandate's own seat is drawn from the Role Brief's title rather than holding its own. */
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
 * is the form. The mandate's own seat shows the role title from the Role Brief and cannot be renamed
 * here or removed, because it is the mandate rather than a seat somebody drew.
 */
export function OrgSeatNode({ data }: NodeProps<Node<OrgSeatData>>) {
  const { seat, roleTitle, childCount, isRoot, canRemove } = data;
  const isMandate = seat.mandateSeat;

  return (
    <div
      style={{ width: NODE_WIDTH, minHeight: NODE_HEIGHT }}
      className={cn(
        "group relative rounded-[10px] border px-3.5 py-2.5 transition",
        isMandate
          ? "border-u-accent bg-u-accent-tint shadow-u-e1"
          : "border-u-border bg-u-bg shadow-u-e1 hover:border-u-text3",
      )}
    >
      <Handle type="target" position={Position.Top} className="!size-2 !border-u-border-strong !bg-u-bg" />

      {isMandate ? (
        <>
          <span className="block truncate text-[13.5px] font-semibold text-u-accent">
            {roleTitle.trim() || "Untitled role"}
          </span>
          <span className="mt-1 inline-block rounded-[4px] bg-u-accent-solid px-1.5 py-0.5 text-[8.5px] font-bold uppercase tracking-[0.08em] text-white">
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
            className="w-full bg-transparent text-[13.5px] font-semibold text-u-text outline-none placeholder:font-normal placeholder:text-u-text3"
          />
          <input
            value={seat.name ?? ""}
            aria-label="Seat holder name"
            placeholder="Name"
            onChange={(event) => data.onPatch(seat.nodeId, { name: event.target.value || null })}
            className="w-full bg-transparent text-[12px] text-u-text2 outline-none placeholder:text-u-text3/70"
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
            icon={ICONS.plus}
            onClick={() => data.onAddParent(seat.nodeId)}
          />
        )}
        {canRemove && (
          <SeatActionButton
            label="Remove this seat"
            icon={ICONS.close}
            tone="offlimits"
            onClick={() => data.onRemove(seat.nodeId)}
          />
        )}
      </span>

      {/* Off to the side, not centred: the source handle sits at bottom-centre, and a button under it
          swallows the click that was meant to start a connection — or, as it turned out, the other way
          round, with the handle intercepting every press of the add button. */}
      <span className="absolute -bottom-2.5 end-2 opacity-0 transition group-focus-within:opacity-100 group-hover:opacity-100">
        <SeatActionButton
          label={isMandate ? "Add a direct report" : "Add a seat below"}
          icon={ICONS.plus}
          onClick={() => data.onAddChild(seat.nodeId)}
        />
      </span>

      {childCount > 0 && (
        <span className="sr-only">
          {childCount} seat{childCount === 1 ? "" : "s"} report here
        </span>
      )}

      <Handle type="source" position={Position.Bottom} className="!size-2 !border-u-border-strong !bg-u-bg" />
    </div>
  );
}

/** The small round control a seat wears for adding a neighbour or removing itself. */
function SeatActionButton({
  label,
  icon,
  tone = "accent",
  onClick,
}: {
  label: string;
  icon: string;
  tone?: "accent" | "offlimits";
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        "grid size-5 place-items-center rounded-full border bg-u-bg text-u-text3 transition",
        tone === "offlimits"
          ? "border-u-border hover:border-u-offlimits hover:text-u-offlimits"
          : "border-u-border hover:border-u-accent hover:text-u-accent",
      )}
    >
      <Icon d={icon} size={11} />
    </button>
  );
}
