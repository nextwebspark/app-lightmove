import { request } from "../../../lib/apiClient";
import type {
  CreateTaskRequest,
  MyTasksResponse,
  MyTasksView,
  TaskPreset,
  TaskPresetChange,
  TaskPresetDraft,
  TaskPresetSuggestion,
  TaskResponse,
  TaskRules,
  TasksResponse,
  UpdateTaskRequest,
} from "./types";

/** Every task read hangs under this prefix, so one invalidation after any write refreshes them all. */
export const TASKS_KEY_PREFIX = ["tasks"] as const;

export const CANDIDATE_TASKS_KEY = (projectId: string, candidateId: string) =>
  [...TASKS_KEY_PREFIX, "candidate", projectId, candidateId] as const;

export const MY_TASKS_KEY = (view: MyTasksView) => [...TASKS_KEY_PREFIX, "mine", view] as const;

export const TASK_PRESETS_KEY = ["task-presets"] as const;

export const TASK_PRESET_SUGGESTIONS_KEY = ["task-presets", "suggestions"] as const;

export const TASK_RULES_KEY = ["task-rules"] as const;

/** The last three done tasks come with every read; `allDone` asks for every one. */
export function getCandidateTasks(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
  allDone = false,
): Promise<TasksResponse> {
  const query = allDone ? "?allDone=true" : "";
  return request<TasksResponse>(`/projects/${projectId}/candidates/${candidateId}/tasks${query}`, { signal });
}

export function createTask(projectId: string, candidateId: string, task: CreateTaskRequest): Promise<TaskResponse> {
  return request<TaskResponse>(`/projects/${projectId}/candidates/${candidateId}/tasks`, {
    method: "POST",
    body: task,
  });
}

export function updateTask(taskId: string, change: UpdateTaskRequest): Promise<TaskResponse> {
  return request<TaskResponse>(`/tasks/${taskId}`, { method: "PATCH", body: change });
}

export function cancelTask(taskId: string): Promise<void> {
  return request<void>(`/tasks/${taskId}`, { method: "DELETE" });
}

/** The caller's own tasks across every position; "today" and "overdue" are counted in the viewer's zone. */
export function getMyTasks(view: MyTasksView, signal?: AbortSignal): Promise<MyTasksResponse> {
  const params = new URLSearchParams({ view, tz: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC" });
  return request<MyTasksResponse>(`/me/tasks?${params.toString()}`, { signal });
}

export function getTaskPresets(signal?: AbortSignal): Promise<TaskPreset[]> {
  return request<TaskPreset[]>("/task-presets", { signal });
}

export function getTaskPresetSuggestions(signal?: AbortSignal): Promise<TaskPresetSuggestion[]> {
  return request<TaskPresetSuggestion[]>("/task-presets/suggestions", { signal });
}

export function createTaskPreset(draft: TaskPresetDraft): Promise<TaskPreset> {
  return request<TaskPreset>("/task-presets", { method: "POST", body: draft });
}

export function updateTaskPreset(presetId: string, change: TaskPresetChange): Promise<TaskPreset> {
  return request<TaskPreset>(`/task-presets/${presetId}`, { method: "PATCH", body: change });
}

export function reorderTaskPresets(presetIds: string[]): Promise<TaskPreset[]> {
  return request<TaskPreset[]>("/task-presets/order", { method: "PUT", body: { presetIds } });
}

export function getTaskRules(signal?: AbortSignal): Promise<TaskRules> {
  return request<TaskRules>("/workspace/task-rules", { signal });
}

export function putTaskRules(rules: TaskRules): Promise<TaskRules> {
  return request<TaskRules>("/workspace/task-rules", { method: "PUT", body: rules });
}
