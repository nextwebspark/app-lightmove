import { request } from "../../../../lib/apiClient";
import type { PublishSubprocessorNotice, SubprocessorNotice, SubprocessorObjection } from "./types";

/** Platform → Sub-processors: a new provider announced to every workspace's admins, and what they answered. */
export const SUBPROCESSOR_NOTICES_KEY = ["platform-subprocessor-notices"] as const;
export const SUBPROCESSOR_OBJECTIONS_KEY = (noticeId: string) =>
  ["platform-subprocessor-notices", noticeId, "objections"] as const;

export function listNotices(signal?: AbortSignal): Promise<SubprocessorNotice[]> {
  return request<SubprocessorNotice[]>("/platform/subprocessor-notices", { signal });
}

/** Asks a fresh two-factor code first, as every action reaching every workspace does. */
export function publishNotice(notice: PublishSubprocessorNotice): Promise<SubprocessorNotice> {
  return request<SubprocessorNotice>("/platform/subprocessor-notices", { method: "POST", body: notice });
}

export function listObjections(noticeId: string, signal?: AbortSignal): Promise<SubprocessorObjection[]> {
  return request<SubprocessorObjection[]>(`/platform/subprocessor-notices/${encodeURIComponent(noticeId)}/objections`, {
    signal,
  });
}
