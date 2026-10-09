import { request } from "../../../lib/apiClient";

export type GettingStartedStepKey =
  | "OPEN_POSITION"
  | "WRITE_BRIEF"
  | "FIND_COMPANIES"
  | "MAP_EXECUTIVES"
  | "CONNECT_MAILBOX"
  | "INVITE_COLLEAGUE";

export interface GettingStartedStep {
  step: GettingStartedStepKey;
  done: boolean;
  skipped: boolean;
  completedAt: string | null;
}

export interface GettingStarted {
  dismissed: boolean;
  focusProjectId: string | null;
  steps: GettingStartedStep[];
}

export const GETTING_STARTED_KEY = ["gettingStarted"] as const;

export function gettingStarted(): Promise<GettingStarted> {
  return request<GettingStarted>("/workspace/getting-started");
}

export function setDismissed(dismissed: boolean): Promise<GettingStarted> {
  return request<GettingStarted>("/workspace/getting-started/dismissed", { method: "PUT", body: { dismissed } });
}

export function setSkipped(step: GettingStartedStepKey, skipped: boolean): Promise<GettingStarted> {
  return request<GettingStarted>(`/workspace/getting-started/steps/${step}/skipped`, {
    method: "PUT",
    body: { skipped },
  });
}
