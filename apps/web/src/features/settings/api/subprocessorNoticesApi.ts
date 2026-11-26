import { request } from "../../../lib/apiClient";

/** Settings → Data & retention's Sub-processor changes: what Uncava announced, and this workspace's objection to each. */
export const SUBPROCESSOR_CHANGES_KEY = ["subprocessor-changes"] as const;

/** The address the notice email's Object link opens: Data & retention with that change's objection open. */
export const OBJECT_PARAM = "object";

export const OBJECTION_LIMIT = 2000;

export interface SubprocessorObjection {
  reason: string;
  objectedAt: string;
  /** Null once that account is erased. */
  objectedBy: string | null;
}

/** Mirrors the API's `WorkspaceSubprocessorNoticeResponse`. */
export interface SubprocessorChange {
  id: string;
  provider: string;
  receives: string;
  purpose: string;
  region: string;
  /** ISO yyyy-MM-dd, the day it takes effect (UTC). */
  effectiveOn: string;
  listUrl: string;
  /** From the effective date on, nobody may object. */
  inEffect: boolean;
  objection: SubprocessorObjection | null;
}

export function getSubprocessorChanges(signal?: AbortSignal): Promise<SubprocessorChange[]> {
  return request<SubprocessorChange[]>("/workspace/subprocessor-notices", { signal });
}

/** Sent again, it replaces the reason given before. */
export function objectToSubprocessorChange(noticeId: string, reason: string): Promise<SubprocessorChange> {
  return request<SubprocessorChange>(`/workspace/subprocessor-notices/${encodeURIComponent(noticeId)}/objection`, {
    method: "PUT",
    body: { reason },
  });
}
