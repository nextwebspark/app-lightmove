import { request } from "../../../lib/apiClient";
import type {
  PlatformConfigVersion,
  PlatformPromptDetail,
  PlatformPromptsResponse,
  PlatformSetting,
  PlatformSettingsResponse,
} from "./types";

/** Settings → Platform → Variables and Prompts: Uncava's own super admins, never a workspace's. */

export const PLATFORM_SETTINGS_KEY = ["platform-settings"] as const;
export const PLATFORM_PROMPTS_KEY = ["platform-prompts"] as const;
export const PLATFORM_PROMPT_KEY = (promptId: string) => ["platform-prompts", promptId] as const;

const SETTINGS = "/platform/settings";
const PROMPTS = "/platform/prompts";

const settingUrl = (key: string) => `${SETTINGS}/${encodeURIComponent(key)}`;
const promptUrl = (promptId: string) => `${PROMPTS}/${encodeURIComponent(promptId)}`;

export function listSettings(signal?: AbortSignal): Promise<PlatformSettingsResponse> {
  return request<PlatformSettingsResponse>(SETTINGS, { signal });
}

export function changeSetting(key: string, value: string): Promise<PlatformSetting> {
  return request<PlatformSetting>(settingUrl(key), { method: "PUT", body: { value } });
}

export function resetSetting(key: string): Promise<PlatformSetting> {
  return request<PlatformSetting>(settingUrl(key), { method: "DELETE" });
}

export function listPrompts(signal?: AbortSignal): Promise<PlatformPromptsResponse> {
  return request<PlatformPromptsResponse>(PROMPTS, { signal });
}

export function getPrompt(promptId: string, signal?: AbortSignal): Promise<PlatformPromptDetail> {
  return request<PlatformPromptDetail>(promptUrl(promptId), { signal });
}

/** `expectedVersion` is the version the editor opened on, null when it opened on the shipped prompt. */
export function savePrompt(promptId: string, body: string, expectedVersion: number | null): Promise<PlatformPromptDetail> {
  return request<PlatformPromptDetail>(promptUrl(promptId), { method: "PUT", body: { body, expectedVersion } });
}

export function restorePromptVersion(promptId: string, version: number): Promise<PlatformPromptDetail> {
  return request<PlatformPromptDetail>(`${promptUrl(promptId)}/versions/${version}/restore`, { method: "POST" });
}

export function revertPrompt(promptId: string): Promise<PlatformPromptDetail> {
  return request<PlatformPromptDetail>(`${promptUrl(promptId)}/override`, { method: "DELETE" });
}

export type PlatformConfigPage = "settings" | "prompts";

/**
 * Either page's Refresh: every instance reloads every override now, rather than within half a minute. Each page
 * asks through its own route, so it needs only its own platform action.
 */
export function refreshPlatformConfig(page: PlatformConfigPage): Promise<PlatformConfigVersion> {
  return request<PlatformConfigVersion>(`${page === "settings" ? SETTINGS : PROMPTS}/refresh`, { method: "POST" });
}
