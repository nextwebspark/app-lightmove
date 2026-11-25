import { request } from "../../../lib/apiClient";
import { getDateTimePreferences } from "../../../lib/datetime/preferences";
import type { ConversationRow, ConversationScope } from "../../outreach/api/conversationApi";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import type { MyTasksView } from "../../tasks/api/types";

/** One row of Home's outreach queue: a position's conversation row, and the position it belongs to. */
export interface MyConversationRow {
  projectId: string;
  positionTitle: string;
  row: ConversationRow;
}

/** Rows in the queue's own order; `truncated` when more wait than were sent. */
export interface MyOutreachQueue {
  rows: MyConversationRow[];
  truncated: boolean;
}

/** Home's task reads: under every task write's invalidation, and apart from My tasks' infinite lists. */
export const HOME_TASKS_KEY = (view: MyTasksView) => [...TASKS_KEY_PREFIX, "home", view] as const;

export const MY_OUTREACH_NEEDS_PREFIX = ["outreach", "me", "needs"] as const;
export const MY_OUTREACH_NEEDS_KEY = (who: ConversationScope) => [...MY_OUTREACH_NEEDS_PREFIX, who] as const;

export function getMyOutreachNeeds(who: ConversationScope, signal?: AbortSignal): Promise<MyOutreachQueue> {
  return request<MyOutreachQueue>(`/me/outreach/needs?who=${who}`, { signal });
}

/**
 * A meeting on the caller's own calendar today. The position fields are null when the executive is on none of the
 * caller's live positions; `joinUrl` only until the call has ended.
 */
export interface MyMeeting {
  id: string;
  title: string | null;
  startsAt: string;
  endsAt: string;
  joinUrl: string | null;
  /** The calendar's own name for the call's video, "Google Meet" or "Zoom". */
  videoProvider: string | null;
  personId: string;
  personName: string | null;
  projectId: string | null;
  candidateId: string | null;
  positionTitle: string | null;
  clientName: string | null;
}

export const MY_MEETINGS_TODAY_KEY = ["meetings", "me", "today"] as const;

export async function getMyMeetingsToday(signal?: AbortSignal): Promise<MyMeeting[]> {
  const tz = encodeURIComponent(getDateTimePreferences().timeZone);
  const { meetings } = await request<{ meetings: MyMeeting[] }>(`/me/meetings/today?tz=${tz}`, { signal });
  return meetings;
}
