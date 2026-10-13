import { ApiRequestError } from "../../../lib/apiClient";
import * as assistantApi from "../api/assistantApi";
import type { AssistantThread } from "../api/types";

export const LATE_ANSWER_POLL_MS = 5_000;
export const LATE_ANSWER_GIVE_UP_MS = 5 * 60_000;

/** An answer whose stream closed before it was written; the server still saves it. */
export type AwaitedAnswer = {
  question: string;
  /** The chat the answer is saved in, which the stream named when it opened. */
  threadId: string;
  /** The chat the panel was showing when the question was asked: null for a new one. */
  askedIn: string | null;
  turnsBefore: number;
};

/** The chat once it holds the answer; null while it is being written; "gone" once the server gave it up. */
export async function findLateAnswer(awaited: AwaitedAnswer): Promise<AssistantThread | "gone" | null> {
  try {
    const thread = await assistantApi.getThread(awaited.threadId);
    return thread.turns.length > awaited.turnsBefore ? thread : null;
  } catch (error) {
    // A new chat whose first question failed is removed, so its id stops resolving.
    if (error instanceof ApiRequestError && error.problem.status === 404) return "gone";
    return null;
  }
}
