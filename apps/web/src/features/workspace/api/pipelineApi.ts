import { request } from "../../../lib/apiClient";
import type {
  PipelineCatalog,
  PipelineRetirePreview,
  PipelineStageChange,
  PipelineStatusChange,
  StatusAutomation,
  StatusAutomationChange,
  StatusAutomationTrigger,
} from "./types";

/** The workspace's pipeline catalog: any member reads it, and every write, an admin's, answers it whole. */

export const PIPELINE_CATALOG_KEY = ["pipeline-catalog"] as const;
export const STATUS_AUTOMATIONS_KEY = ["pipeline-automations"] as const;

export function pipelineCatalog(signal?: AbortSignal): Promise<PipelineCatalog> {
  return request<PipelineCatalog>("/workspace/pipeline", { signal });
}

export function createStage(label: string, colour?: string): Promise<PipelineCatalog> {
  return request<PipelineCatalog>("/workspace/pipeline/stages", { method: "POST", body: { label, colour } });
}

export function updateStage(stageId: string, change: PipelineStageChange): Promise<PipelineCatalog> {
  return request<PipelineCatalog>(`/workspace/pipeline/stages/${stageId}`, { method: "PATCH", body: change });
}

/** Every live stage id, the closed stage last. */
export function reorderStages(ids: string[]): Promise<PipelineCatalog> {
  return request<PipelineCatalog>("/workspace/pipeline/stages/order", { method: "PUT", body: { ids } });
}

export function createStatus(stageId: string, label: string, colour?: string): Promise<PipelineCatalog> {
  return request<PipelineCatalog>("/workspace/pipeline/statuses", {
    method: "POST",
    body: { stageId, label, colour },
  });
}

export function updateStatus(statusId: string, change: PipelineStatusChange): Promise<PipelineCatalog> {
  return request<PipelineCatalog>(`/workspace/pipeline/statuses/${statusId}`, { method: "PATCH", body: change });
}

/** Every live status id of the stage. */
export function reorderStatuses(stageId: string, ids: string[]): Promise<PipelineCatalog> {
  return request<PipelineCatalog>(`/workspace/pipeline/stages/${stageId}/statuses/order`, {
    method: "PUT",
    body: { ids },
  });
}

export function stageRetirePreview(stageId: string): Promise<PipelineRetirePreview> {
  return request<PipelineRetirePreview>(`/workspace/pipeline/stages/${stageId}/retire-preview`);
}

export function statusRetirePreview(statusId: string): Promise<PipelineRetirePreview> {
  return request<PipelineRetirePreview>(`/workspace/pipeline/statuses/${statusId}/retire-preview`);
}

/** Staff only: how the firm moves its candidates is not a client seat's to read. */
export function statusAutomations(signal?: AbortSignal): Promise<StatusAutomation[]> {
  return request<StatusAutomation[]>("/workspace/pipeline/automations", { signal });
}

export function updateStatusAutomation(
  trigger: StatusAutomationTrigger,
  change: StatusAutomationChange,
): Promise<StatusAutomation[]> {
  return request<StatusAutomation[]>(`/workspace/pipeline/automations/${trigger}`, { method: "PUT", body: change });
}
