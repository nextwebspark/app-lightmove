import * as assistantApi from "../api/assistantApi";
import type { AssistantThread } from "../api/types";

export const LATE_ANSWER_POLL_MS = 5_000;
export const LATE_ANSWER_GIVE_UP_MS = 5 * 60_000;

const MAX_TITLE = 80;
const CLOCK_SKEW_MS = 60_000;

/** An answer whose stream closed before it was written; the server still saves it. */
export type AwaitedAnswer = {
  question: string;
  /** Null for a question that founds a new chat, whose id only the saved answer carries. */
  threadId: string | null;
  turnsBefore: number;
  askedAt: number;
};

/** The title the server gives a chat founded by this question (`AssistantService.titleOf`). */
export function chatTitleOf(question: string): string {
  const flattened = question.replace(/\s+/g, " ").trim();
  return flattened.length <= MAX_TITLE ? flattened : flattened.slice(0, MAX_TITLE).trim();
}

/** The chat with the awaited answer in it, or null while it is still being written. */
export async function findLateAnswer(awaited: AwaitedAnswer, projectId: string): Promise<AssistantThread | null> {
  if (awaited.threadId) {
    const thread = await assistantApi.getThread(awaited.threadId);
    return thread.turns.length > awaited.turnsBefore ? thread : null;
  }
  const title = chatTitleOf(awaited.question);
  const summaries = (await assistantApi.listThreads(projectId)) ?? [];
  const founded = summaries.find(
    (summary) => summary.title === title && Date.parse(summary.updatedAt) >= awaited.askedAt - CLOCK_SKEW_MS,
  );
  return founded ? assistantApi.getThread(founded.id) : null;
}
