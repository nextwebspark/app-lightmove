import { request } from "../../../lib/apiClient";

/**
 * The caller's own Zoom account, for the link Book a call puts on an invite. Separate from the mailbox, and every
 * route reads the caller's own row.
 */

export const ZOOM_KEY = ["outreach", "zoom"] as const;

export interface ZoomAccount {
  /** False where the workspace has no Zoom app: nothing can be connected. */
  offered: boolean;
  /** Null while nothing is connected; `ERROR` once Zoom refused the stored token. */
  status: "ACTIVE" | "ERROR" | null;
  connectedAt: string | null;
}

export function getZoom(signal?: AbortSignal): Promise<ZoomAccount> {
  return request<ZoomAccount>("/outreach/zoom", { signal });
}

/** Also sets the cookie that ties Zoom's answer to this browser. */
export function startZoomConnect(): Promise<{ authorizationUrl: string }> {
  return request<{ authorizationUrl: string }>("/outreach/zoom/connect", { method: "POST" });
}

export function disconnectZoom(): Promise<void> {
  return request<void>("/outreach/zoom", { method: "DELETE" });
}
