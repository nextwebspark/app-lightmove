import { request } from "../../../../lib/apiClient";
import type {
  AiModel,
  PromptDetail,
  PromptOverview,
  PromptTrialResult,
  PromptVersionBody,
  SavePromptVersionRequest,
} from "./types";

/** Platform → Prompts: the AI prompts every workspace's calls are answered with. */
export const PROMPTS_KEY = ["platform-prompts"] as const;
export const PROMPT_KEY = (promptId: string) => ["platform-prompts", promptId] as const;
export const PROMPT_BODY_KEY = (promptId: string, version: number) =>
  ["platform-prompts", promptId, "version", version] as const;
export const AI_MODELS_KEY = ["platform-ai-models"] as const;

const promptUrl = (promptId: string) => `/platform/prompts/${encodeURIComponent(promptId)}`;

export function listPrompts(signal?: AbortSignal): Promise<PromptOverview[]> {
  return request<PromptOverview[]>("/platform/prompts", { signal });
}

export function getPrompt(promptId: string, signal?: AbortSignal): Promise<PromptDetail> {
  return request<PromptDetail>(promptUrl(promptId), { signal });
}

export function getPromptBody(promptId: string, version: number, signal?: AbortSignal): Promise<PromptVersionBody> {
  return request<PromptVersionBody>(`${promptUrl(promptId)}/versions/${version}`, { signal });
}

export function listAiModels(signal?: AbortSignal): Promise<AiModel[]> {
  return request<AiModel[]>("/platform/ai-models", { signal });
}

/** Always a new version; what serves does not change. */
export function savePromptVersion(promptId: string, version: SavePromptVersionRequest): Promise<PromptDetail> {
  return request<PromptDetail>(`${promptUrl(promptId)}/versions`, { method: "POST", body: version });
}

export function tryPromptVersion(promptId: string, version: number): Promise<PromptTrialResult> {
  return request<PromptTrialResult>(`${promptUrl(promptId)}/versions/${version}/trial`, { method: "POST" });
}

/**
 * Puts a version live, or rolls back to an earlier one. The server asks for a fresh two-factor code first, and for a
 * `reason` unless a run on the holdout cases found the version no worse than the one serving.
 */
export function putPromptLive(promptId: string, version: number, reason: string | null): Promise<PromptDetail> {
  return request<PromptDetail>(`${promptUrl(promptId)}/production`, {
    method: "PUT",
    body: reason === null ? { version } : { version, reason },
  });
}
