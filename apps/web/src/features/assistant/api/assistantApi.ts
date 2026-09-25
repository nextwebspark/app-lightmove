import { ApiRequestError, request, streamEvents } from "../../../lib/apiClient";
import type { BulkAddResult, TriageCompanyStatus } from "../../triage/api/types";
import type {
  AssistantProposal,
  AssistantStarters,
  AssistantThread,
  AssistantThreadSummary,
  AssistantTurn,
  LiveStep,
} from "./types";

export const ASSISTANT_THREAD_KEY = (threadId: string) => ["assistant", "thread", threadId] as const;
export const ASSISTANT_THREADS_KEY = (projectId: string) => ["assistant", "threads", projectId] as const;
export const ASSISTANT_STARTERS_KEY = (projectId: string) => ["assistant", "starters", projectId] as const;

export function listStarters(projectId: string): Promise<AssistantStarters> {
  return request<AssistantStarters>(`/projects/${projectId}/assistant/starters`);
}

export function listThreads(projectId: string): Promise<AssistantThreadSummary[]> {
  return request<AssistantThreadSummary[]>(`/projects/${projectId}/assistant/threads`);
}

export function getThread(threadId: string): Promise<AssistantThread> {
  return request<AssistantThread>(`/assistant/threads/${threadId}`);
}

/**
 * Asks, and hands each step to `onStep` as the server reports it ("Searching retail companies in
 * …", then its count), the company card to `onProposal` the moment it is made, and each piece of the
 * answer's text to `onAnswer` as the model writes it. Resolves with the saved turn, whose answer is
 * exactly those pieces joined.
 *
 * <p>Not cancellable: the server saves the answer whether or not anyone is still reading.
 */
export async function ask(
  projectId: string,
  question: string,
  threadId: string | null,
  onStep: (step: LiveStep) => void,
  onProposal: (proposal: AssistantProposal) => void,
  onAnswer: (text: string) => void,
): Promise<AssistantTurn> {
  const received: { turn?: AssistantTurn; failedCode?: string } = {};
  await streamEvents(
    `/projects/${projectId}/assistant/ask`,
    (event) => {
      if (event.name === "step") onStep(JSON.parse(event.data) as LiveStep);
      if (event.name === "proposal") onProposal(JSON.parse(event.data) as AssistantProposal);
      if (event.name === "answer") onAnswer((JSON.parse(event.data) as { text: string }).text);
      if (event.name === "done") received.turn = JSON.parse(event.data) as AssistantTurn;
      if (event.name === "failed") received.failedCode = (JSON.parse(event.data) as { code: string }).code;
    },
    undefined,
    { method: "POST", body: { question, threadId } },
  );
  if (received.turn) return received.turn;
  // A stream that ended with no event lost its connection, not its answer: the server finishes and
  // saves it regardless, so it reads as still answering rather than as a failure to retry.
  throw new ApiRequestError({
    code: received.failedCode ?? "ASSISTANT_STILL_ANSWERING",
    detail: "The assistant could not answer",
    status: 503,
    correlationId: "none",
  });
}

export function acceptProposal(
  turnId: string,
  companyIds: string[],
  status: TriageCompanyStatus,
): Promise<BulkAddResult> {
  return request<BulkAddResult>(`/assistant/turns/${turnId}/accept`, {
    method: "POST",
    body: { companyIds, status },
  });
}
