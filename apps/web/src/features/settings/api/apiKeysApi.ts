import { request } from "../../../lib/apiClient";
import type { ApiKey, CreateApiKeyRequest, CreatedApiKey } from "./types";

/** Settings → API keys. `all` lists every key in the workspace, which only WORKSPACE_MANAGE may ask. */

export const API_KEYS_KEY = ["workspace", "api-keys"] as const;

export function apiKeys(all: boolean, signal?: AbortSignal): Promise<ApiKey[]> {
  return request<ApiKey[]>(`/workspace/api-keys${all ? "?all=true" : ""}`, { signal });
}

export function createApiKey(payload: CreateApiKeyRequest): Promise<CreatedApiKey> {
  return request<CreatedApiKey>("/workspace/api-keys", { method: "POST", body: payload });
}

export function revokeApiKey(keyId: string): Promise<void> {
  return request<void>(`/workspace/api-keys/${keyId}`, { method: "DELETE" });
}
