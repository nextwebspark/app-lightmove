import { request } from "../../../lib/apiClient";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import type { PipelinePage, PipelineQuery, PipelineRankMove, PipelineRow } from "./types";

/**
 * A position's pipeline — staff-only, the position's WORK_EXECUTE. Its key hangs under the position's
 * candidate prefix, so every write that refreshes the mandate's people refreshes the pipeline too.
 */
export const PIPELINE_KEY_PREFIX = (projectId: string) => [...CANDIDATES_KEY_PREFIX(projectId), "pipeline"] as const;

export const PIPELINE_KEY = (projectId: string, query: PipelineQuery) =>
  [...PIPELINE_KEY_PREFIX(projectId), query.q, query.pipelineStage] as const;

export const NO_PIPELINE_QUERY: PipelineQuery = { q: "", pipelineStage: "" };

export function getPipeline(projectId: string, query: PipelineQuery, signal?: AbortSignal): Promise<PipelinePage> {
  const params = new URLSearchParams();
  if (query.q.trim()) params.set("q", query.q.trim());
  if (query.pipelineStage) params.set("pipelineStage", query.pipelineStage);
  const search = params.toString();
  return request<PipelinePage>(`/projects/${projectId}/pipeline${search ? `?${search}` : ""}`, { signal });
}

export function movePipelineRank(projectId: string, candidateId: string, move: PipelineRankMove): Promise<PipelineRow> {
  return request<PipelineRow>(`/projects/${projectId}/candidates/${candidateId}/pipeline-rank`, {
    method: "PUT",
    body: move,
  });
}

export function setClientVisibility(projectId: string, candidateId: string, clientVisible: boolean): Promise<PipelineRow> {
  return request<PipelineRow>(`/projects/${projectId}/candidates/${candidateId}/client-visibility`, {
    method: "PUT",
    body: { clientVisible },
  });
}
