import { request } from "../../../lib/apiClient";
import type { OAuthGrant } from "./types";

export const OAUTH_GRANTS_KEY = ["workspace", "oauth-grants"] as const;

/** `all` lists the workspace's every connection, which WORKSPACE_MANAGE alone may ask. */
export function oauthGrants(all: boolean, signal?: AbortSignal): Promise<OAuthGrant[]> {
  return request<OAuthGrant[]>(`/workspace/oauth-grants${all ? "?all=true" : ""}`, { signal });
}

export function revokeOAuthGrant(grantId: string): Promise<void> {
  return request<void>(`/workspace/oauth-grants/${grantId}`, { method: "DELETE" });
}
