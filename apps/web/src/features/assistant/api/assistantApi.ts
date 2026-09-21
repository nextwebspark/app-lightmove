import { request } from "../../../lib/apiClient";
import type { AssistantThread, AssistantTurn } from "./types";

/**
 * The caller's own assistant conversations.
 *
 * <p>Asking answers **202 with a RUNNING turn**, not the answer: a turn with tools runs 30–180s,
 * which no single response can hold. What comes back is the turn to open a stream on.
 */
export const ASSISTANT_THREAD_KEY = (threadId: string) => ["assistant", "thread", threadId] as const;

export function ask(question: string, projectId: string | null): Promise<AssistantTurn> {
  return request<AssistantTurn>("/assistant/ask", {
    method: "POST",
    body: { question, projectId },
  });
}

export function askIn(threadId: string, question: string): Promise<AssistantTurn> {
  return request<AssistantTurn>(`/assistant/threads/${threadId}/ask`, {
    method: "POST",
    body: { question },
  });
}

export function getThread(threadId: string): Promise<AssistantThread> {
  return request<AssistantThread>(`/assistant/threads/${threadId}`);
}
