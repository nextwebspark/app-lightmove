import { moveItem } from "../../../lib/useDragReorder";
import type { PipelineCatalog } from "../../workspace/api/types";
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
