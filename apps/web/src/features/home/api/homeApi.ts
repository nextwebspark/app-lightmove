import { request } from "../../../lib/apiClient";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import type { MyTasksView } from "../../tasks/api/types";
import type { ConversationRow, ConversationScope } from "../../outreach/api/conversationApi";

/** One row of Home's outreach queue: a position's conversation row, and the position it belongs to. */
export interface MyConversationRow {
  projectId: string;
  positionTitle: string;
  row: ConversationRow;
}

export interface MyOutreachQueue {
  rows: MyConversationRow[];
  /** How many wait in all, past the rows sent. */
  total: number;
  truncated: boolean;
}

/** Home's task reads: under every task write's invalidation, and apart from My tasks' infinite lists. */
export const HOME_TASKS_KEY = (view: MyTasksView) => [...TASKS_KEY_PREFIX, "home", view] as const;

export const MY_OUTREACH_NEEDS_KEY = (who: ConversationScope) => ["outreach", "me", "needs", who] as const;

export function getMyOutreachNeeds(who: ConversationScope, signal?: AbortSignal): Promise<MyOutreachQueue> {
  return request<MyOutreachQueue>(`/me/outreach/needs?who=${who}`, { signal });
}
