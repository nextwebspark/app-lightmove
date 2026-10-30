import type { CandidatePipelineStage } from "../../candidates/api/types";
import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../../workspace/api/types";
import type { PipelineRankMove, PipelineRow } from "../api/types";

export function shortDate(moment: Date | string): string {
  return new Date(moment).toLocaleDateString("en-GB", { day: "2-digit", month: "short" });
}

/** Whole days since the status changed, never negative. */
export function daysInStage(statusChangedAt: string, now: Date = new Date()): number {
  const elapsed = now.getTime() - new Date(statusChangedAt).getTime();
  return Math.max(0, Math.floor(elapsed / 86_400_000));
}

/** Where a dropped card goes: its stage, the status it is given (null keeps its own), and the card it lands before. */
export interface BoardTarget {
  stage: CandidatePipelineStage;
  status: Pick<PipelineStatus, "key" | "label" | "colour" | "outOfProcess"> | null;
  beforeId: string | null;
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
  const placed = restatus ? withStatus(moved, status, target.stage, now) : moved;
  const reordered = insertBefore(rows.filter((row) => row !== moved), placed, target);
  const landed = neighboursIn(reordered, placed);
  if (!restatus && sameNeighbours(neighboursIn(rows, moved), landed)) return null;
  return {
    candidateId: movedId,
    fromStageKey: moved.pipelineStage.key,
    stageKey: target.stage.key,
    stageLabel: target.stage.label,
    status: restatus ? status.key : null,
    move: landed.afterCandidateId || landed.beforeCandidateId ? landed : null,
    rows: reordered,
  };
}

function withStatus(
  row: PipelineRow,
  status: NonNullable<BoardTarget["status"]>,
  stage: CandidatePipelineStage,
  now: Date,
): PipelineRow {
  return {
    ...row,
    status: status.key,
    statusLabel: status.label,
    statusColour: status.colour,
    outOfProcess: status.outOfProcess,
    pipelineStage: stage,
    statusChangedAt: now.toISOString(),
  };
}

/** Before the target's card when it sits in the target's stage, otherwise at the end of that stage. */
function insertBefore(rows: readonly PipelineRow[], placed: PipelineRow, target: BoardTarget): PipelineRow[] {
  const before = target.beforeId === null ? -1 : rows.findIndex((row) => row.candidateId === target.beforeId);
  const at =
    before !== -1 && rows[before].pipelineStage.key === target.stage.key ? before : endOfStage(rows, target.stage);
  return [...rows.slice(0, at), placed, ...rows.slice(at)];
}

/** The rows either side of this one within its own stage. */
function neighboursIn(rows: readonly PipelineRow[], row: PipelineRow): PipelineRankMove {
  const stage = rows.filter((candidate) => candidate.pipelineStage.key === row.pipelineStage.key);
  const at = stage.indexOf(row);
  return {
    afterCandidateId: stage[at - 1]?.candidateId ?? null,
    beforeCandidateId: stage[at + 1]?.candidateId ?? null,
  };
}

function sameNeighbours(a: PipelineRankMove, b: PipelineRankMove): boolean {
  return a.afterCandidateId === b.afterCandidateId && a.beforeCandidateId === b.beforeCandidateId;
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
  /** The stage the status sits under, since a way out may sit under any stage. */
  stageLabel: string;
  rows: PipelineRow[];
}

/** Everyone out of process grouped by status, in the catalog's order; only statuses someone holds. */
export function inactiveGroupsOf(rows: readonly PipelineRow[], catalog: PipelineCatalog): InactiveGroup[] {
  const out = rows.filter((row) => row.outOfProcess);
  const order = catalog.stages.flatMap((stage) => stage.statuses.map((status) => status.key));
  const groups = new Map<string, InactiveGroup>();
  for (const row of out) {
    const group = groups.get(row.status) ?? {
      status: row.status,
      label: row.statusLabel,
      stageLabel: row.pipelineStage.label,
      rows: [],
    };
    group.rows.push(row);
    groups.set(row.status, group);
  }
  const rank = (key: string) => {
    const index = order.indexOf(key);
    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
  };
  return [...groups.values()].sort((a, b) => rank(a.status) - rank(b.status));
}

/** A way out of the process a card can be given, with the stage it sits under. */
export interface OutOfProcessChoice {
  stage: CandidatePipelineStage;
  status: PipelineStatus;
}

/**
 * Every live status out of process, the card's own stage's first and then in pipeline order: what a card
 * dropped on Inactive itself is offered.
 */
export function outOfProcessChoicesFor(catalog: PipelineCatalog, stageKey: string): OutOfProcessChoice[] {
  const choices = catalog.stages
    .filter((stage) => !stage.retired)
    .flatMap((stage) =>
      stage.statuses
        .filter((status) => status.outOfProcess && !status.retired)
        .map((status) => ({ stage: candidateStageOf(stage), status })),
    );
  return [
    ...choices.filter((choice) => choice.stage.key === stageKey),
    ...choices.filter((choice) => choice.stage.key !== stageKey),
  ];
}

export function candidateStageOf({ key, label, colour, position }: PipelineStage): CandidatePipelineStage {
  return { key, label, colour, position };
}
