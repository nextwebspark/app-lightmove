import { ApiRequestError, request, streamEvents } from "../../../lib/apiClient";
import type { BulkAddResult, TriageCompanyStatus } from "../../triage/api/types";
import type { AssistantThread, AssistantThreadSummary, AssistantTurn, LiveStep } from "./types";

export const ASSISTANT_THREAD_KEY = (threadId: string) => ["assistant", "thread", threadId] as const;
export const ASSISTANT_THREADS_KEY = (projectId: string) => ["assistant", "threads", projectId] as const;

export function listThreads(projectId: string): Promise<AssistantThreadSummary[]> {
  return request<AssistantThreadSummary[]>(`/projects/${projectId}/assistant/threads`);
}

export function getThread(threadId: string): Promise<AssistantThread> {
  return request<AssistantThread>(`/assistant/threads/${threadId}`);
}

/**
 * Asks, and hands each step to `onStep` as the server reports it ("Searching retail companies in
 * …", then its count). Resolves with the saved turn once the answer is ready.
 */
export async function ask(
  projectId: string,
  question: string,
  threadId: string | null,
  onStep: (step: LiveStep) => void,
): Promise<AssistantTurn> {
  const received: { turn?: AssistantTurn; failedCode?: string } = {};
  await streamEvents(
    `/projects/${projectId}/assistant/ask`,
    (event) => {
      if (event.name === "step") onStep(JSON.parse(event.data) as LiveStep);
      if (event.name === "done") received.turn = JSON.parse(event.data) as AssistantTurn;
      if (event.name === "failed") received.failedCode = (JSON.parse(event.data) as { code: string }).code;
    },
    new AbortController().signal,
    { method: "POST", body: { question, threadId } },
  );
  if (received.turn) return received.turn;
  // No `done`: the model failed, or the answer outran the 55s stream.
  throw new ApiRequestError({
    code: received.failedCode ?? "ASSISTANT_UNAVAILABLE",
    detail: "The assistant could not answer",
    status: 503,
    correlationId: "none",
  });
}

export function acceptProposal(
  turnId: string,
  apolloAccountIds: string[],
  status: TriageCompanyStatus,
): Promise<BulkAddResult> {
  return request<BulkAddResult>(`/assistant/turns/${turnId}/accept`, {
    method: "POST",
    body: { apolloAccountIds, status },
  });
}
