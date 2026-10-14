import { moveItem } from "../../../lib/moveItem";
import type { CandidatePipelineStage } from "../../candidates/api/types";
import type { PipelineCatalog, PipelineStatus } from "../../workspace/api/types";
import type { PipelineRankMove, PipelineRow } from "../api/types";

export type DueTone = "overdue" | "today" | "later";

/** Overdue once its day has passed, due today all of today, and plain from tomorrow on. */
export function dueToneOf(dueAt: string, now: Date = new Date()): DueTone {
  const due = new Date(dueAt);
  if (sameDay(due, now)) return "today";
  return due.getTime() < now.getTime() ? "overdue" : "later";
}

/** "Overdue · 03 Oct", "Today · 16:00", or the date alone. */
export function dueLabelOf(dueAt: string, now: Date = new Date()): string {
  const due = new Date(dueAt);
  const tone = dueToneOf(dueAt, now);
  if (tone === "today") return `Today · ${due.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`;
  const day = shortDate(due);
  return tone === "overdue" ? `Overdue · ${day}` : day;
}

export function shortDate(moment: Date | string): string {
  return new Date(moment).toLocaleDateString("en-GB", { day: "2-digit", month: "short" });
}

/** Whole days since the status changed, never negative. */
export function daysInStage(statusChangedAt: string, now: Date = new Date()): number {
  const elapsed = now.getTime() - new Date(statusChangedAt).getTime();
  return Math.max(0, Math.floor(elapsed / 86_400_000));
}

/**
 * A drag of one row onto another: the new order and the two rows it now sits between. A row moves only
 * among its own stage's rows, since a stage is changed by a status and never by a drag in the list.
 */
export function rankMoveOf(
  rows: readonly PipelineRow[],
  movedId: string,
  targetId: string,
): { rows: PipelineRow[]; move: PipelineRankMove } | null {
  const moved = rows.find((row) => row.candidateId === movedId);
  const target = rows.find((row) => row.candidateId === targetId);
  if (!moved || !target || moved === target || moved.pipelineStage.key !== target.pipelineStage.key) return null;
  const from = rows.indexOf(moved);
  const to = rows.indexOf(target);
  const reordered = moveItem(rows, from, to);
  const sameStage = reordered.filter((row) => row.pipelineStage.key === moved.pipelineStage.key);
  const at = sameStage.indexOf(moved);
  return {
    rows: reordered,
    move: {
      afterCandidateId: sameStage[at - 1]?.candidateId ?? null,
      beforeCandidateId: sameStage[at + 1]?.candidateId ?? null,
    },
  };
}

/** Where a dropped card goes: its stage, the status it is given (null keeps its own), and the card it lands before. */
export interface BoardTarget {
  stage: CandidatePipelineStage;
  status: Pick<PipelineStatus, "key" | "label" | "colour"> | null;
  beforeId: string | null;
  /** False for a drop that sets a status and leaves the order where the stage already keeps it. */
  ranked: boolean;
}

/** A card dropped on the board: the order the board shows at once, and the writes that make it so. */
export interface BoardPlacement {
  candidateId: string;
  fromStageKey: string;
  stageKey: string;
  stageLabel: string;
  status: string | null;
  move: PipelineRankMove | null;
  rows: PipelineRow[];
}

/**
 * A card dropped before another, or at the end of a stage: the board's new order, with the card already
 * wearing its new status, and its neighbours in that stage for the rank write. Null when it lands where it was.
 */
export function boardPlacementOf(
  rows: readonly PipelineRow[],
  movedId: string,
  target: BoardTarget,
  now: Date = new Date(),
): BoardPlacement | null {
  const moved = rows.find((row) => row.candidateId === movedId);
  if (!moved || target.beforeId === movedId) return null;
  const status = target.status;
  const restatus = status !== null && status.key !== moved.status;
  const placed: PipelineRow = restatus
    ? {
        ...moved,
        status: status.key,
        statusLabel: status.label,
        statusColour: status.colour,
        pipelineStage: target.stage,
        statusChangedAt: now.toISOString(),
      }
    : moved;
  const others = rows.filter((row) => row !== moved);
  const inStage = (row: PipelineRow) => row.pipelineStage.key === target.stage.key;
  const before = target.beforeId === null ? -1 : others.findIndex((row) => row.candidateId === target.beforeId);
  const at = before !== -1 && inStage(others[before]) ? before : endOfStage(others, target.stage);
  const reordered = [...others.slice(0, at), placed, ...others.slice(at)];

  const stageRows = reordered.filter(inStage);
  const slot = stageRows.indexOf(placed);
  const after = stageRows[slot - 1]?.candidateId ?? null;
  const next = stageRows[slot + 1]?.candidateId ?? null;
  if (!restatus) {
    const held = rows.filter(inStage);
    const heldSlot = held.indexOf(moved);
    const unmoved =
      (held[heldSlot - 1]?.candidateId ?? null) === after && (held[heldSlot + 1]?.candidateId ?? null) === next;
    if (unmoved || !target.ranked) return null;
  }
  return {
    candidateId: movedId,
    fromStageKey: moved.pipelineStage.key,
    stageKey: target.stage.key,
    stageLabel: target.stage.label,
    status: restatus ? status.key : null,
    move: target.ranked && stageRows.length > 1 ? { afterCandidateId: after, beforeCandidateId: next } : null,
    rows: reordered,
  };
}

/** Just past the stage's last row, or where the stage would begin when nobody is in it yet. */
function endOfStage(rows: readonly PipelineRow[], stage: CandidatePipelineStage): number {
  const last = rows.findLastIndex((row) => row.pipelineStage.key === stage.key);
  if (last !== -1) return last + 1;
  const later = rows.findIndex((row) => row.pipelineStage.position > stage.position);
  return later === -1 ? rows.length : later;
}

export interface InactiveGroup {
  status: string;
  label: string;
  rows: PipelineRow[];
}

/** The closed stage's people grouped by status, in the catalog's order; only statuses someone holds. */
export function inactiveGroupsOf(rows: readonly PipelineRow[], catalog: PipelineCatalog): InactiveGroup[] {
  const closed = rows.filter((row) => row.pipelineStage.kind === "closed");
  const order = catalog.stages.flatMap((stage) => stage.statuses.map((status) => status.key));
  const groups = new Map<string, InactiveGroup>();
  for (const row of closed) {
    const group = groups.get(row.status) ?? { status: row.status, label: row.statusLabel, rows: [] };
    group.rows.push(row);
    groups.set(row.status, group);
  }
  const rank = (key: string) => {
    const index = order.indexOf(key);
    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
  };
  return [...groups.values()].sort((a, b) => rank(a.status) - rank(b.status));
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}
