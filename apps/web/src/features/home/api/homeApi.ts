import { request } from "../../../lib/apiClient";
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

export const MY_OUTREACH_NEEDS_KEY = (who: ConversationScope) => ["outreach", "me", "needs", who] as const;

export function getMyOutreachNeeds(who: ConversationScope, signal?: AbortSignal): Promise<MyOutreachQueue> {
  return request<MyOutreachQueue>(`/me/outreach/needs?who=${who}`, { signal });
}
