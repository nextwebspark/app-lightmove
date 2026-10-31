import type { CandidatePipelineStage } from "../../candidates/api/types";
import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../../workspace/api/types";
import type { PipelineRankMove, PipelineRow } from "../api/types";

export function shortDate(moment: Date | string): string {
  return new Date(moment).toLocaleDateString("en-GB", { day: "2-digit", month: "short" });
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

/** A stage's live statuses out of process: the ways out a card dropped under that stage is offered. */
export function waysOutOf(catalog: PipelineCatalog, stageKey: string): PipelineStatus[] {
  const stage = catalog.stages.find((candidate) => candidate.key === stageKey);
  if (!stage || stage.retired) return [];
  return stage.statuses.filter((status) => status.outOfProcess && !status.retired);
}

/** One cell of the board's Out of process band: the people out of process under one stage, and its ways out. */
export interface OutOfProcessCell {
  stage: CandidatePipelineStage;
  /** False for a stage with no column above it, whose cell names it instead. */
  underColumn: boolean;
  waysOut: PipelineStatus[];
  rows: PipelineRow[];
}

/**
 * The band's cells: one under each column, in the columns' order, then one for each stage with no column that
 * offers a way out or holds someone — a stage of ways out alone, like the old Declined.
 */
export function outOfProcessCellsOf(
  rows: readonly PipelineRow[],
  columns: readonly CandidatePipelineStage[],
  catalog: PipelineCatalog,
): OutOfProcessCell[] {
  const out = rows.filter((row) => row.outOfProcess);
  const cellOf = (stage: CandidatePipelineStage, underColumn: boolean): OutOfProcessCell => ({
    stage,
    underColumn,
    waysOut: waysOutOf(catalog, stage.key),
    rows: out.filter((row) => row.pipelineStage.key === stage.key),
  });
  const columnKeys = new Set(columns.map((column) => column.key));
  const uncolumned = new Map<string, CandidatePipelineStage>();
  for (const stage of catalog.stages) {
    if (!columnKeys.has(stage.key) && waysOutOf(catalog, stage.key).length > 0) {
      uncolumned.set(stage.key, candidateStageOf(stage));
    }
  }
  for (const row of out) {
    if (!columnKeys.has(row.pipelineStage.key) && !uncolumned.has(row.pipelineStage.key)) {
      uncolumned.set(row.pipelineStage.key, row.pipelineStage);
    }
  }
  return [
    ...columns.map(({ key, label, colour, position }) => cellOf({ key, label, colour, position }, true)),
    ...[...uncolumned.values()].sort((a, b) => a.position - b.position).map((stage) => cellOf(stage, false)),
  ];
}

export function candidateStageOf({ key, label, colour, position }: PipelineStage): CandidatePipelineStage {
  return { key, label, colour, position };
}
