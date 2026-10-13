import type { CandidatePipelineStage } from "../../candidates/api/types";

/** What has to happen next with someone, when, and who said so. */
export interface NextAction {
  text: string;
  dueAt: string | null;
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
  pipelineStage: CandidatePipelineStage;
  addedAt: string;
  statusChangedAt: string;
  nextAction: NextAction | null;
  clientVisible: boolean;
  /** When research last landed; the photo is asked for only once it has. */
  enrichedAt?: string | null;
}

/** Every live stage with how many of the rows sit in it — the search narrows it, the stage filter does not. */
export interface PipelineStageCount extends CandidatePipelineStage {
  count: number;
}

export interface PipelinePage {
  rows: PipelineRow[];
  stageCounts: PipelineStageCount[];
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
