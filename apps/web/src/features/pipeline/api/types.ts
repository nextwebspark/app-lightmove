import type { CandidatePipelineStage } from "../../candidates/api/types";
import type { TaskKind, TaskPerson } from "../../tasks/api/types";

/** The executive's earliest open task on the position: what the card and the list row show as Next action. */
export interface CandidateNextAction {
  taskId: string;
  text: string;
  kind: TaskKind;
  dueAt: string | null;
  assignee: TaskPerson;
  /** Every open task on them here, this one included. */
  openCount: number;
  setByName: string | null;
}

/** One executive on a position's pipeline. Rows arrive ordered by stage, then by the team's own order. */
export interface PipelineRow {
  candidateId: string;
  personId: string;
  fullName: string;
  title: string | null;
  companyName: string | null;
  triageCompanyId: string | null;
  status: string;
  statusLabel: string;
  statusColour: string;
  /** Out of process: drawn in Inactive, whatever stage the status sits under. */
  outOfProcess: boolean;
  pipelineStage: CandidatePipelineStage;
  addedAt: string;
  statusChangedAt: string;
  nextAction: CandidateNextAction | null;
  clientVisible: boolean;
  /** When research last landed; the photo is asked for only once it has. */
  enrichedAt?: string | null;
}

/** Every live stage with how many rows sit in it in process — the search narrows it, the stage filter does not. */
export interface PipelineStageCount extends CandidatePipelineStage {
  count: number;
}

export interface PipelinePage {
  rows: PipelineRow[];
  stageCounts: PipelineStageCount[];
  /** Rows at a status out of process, whatever their stage. */
  inactiveCount: number;
  total: number;
}

export interface PipelineQuery {
  q: string;
  pipelineStage: string;
}

/** The moved row lands between these two; null at either end. */
export interface PipelineRankMove {
  afterCandidateId: string | null;
  beforeCandidateId: string | null;
}
