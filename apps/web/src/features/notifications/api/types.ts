import type { TaskNote, TaskPerson } from "../../tasks/api/types";

/** What a bell row says happened. The SPA composes the sentence; a note is named, never quoted. */
export type NotificationKind =
  | "task_assigned"
  | "task_due_soon"
  | "task_overdue"
  | "mentioned"
  | "outreach_replied"
  | "outreach_bounced"
  | "call_booked"
  | "added_to_team"
  | "tasks_moved"
  | "meeting_notes_ready";

export interface NotificationResponse {
  id: string;
  kind: NotificationKind;
  /** How many events this row stands for while unread — "3 replies on CFO Search". */
  count: number;
  /** Null when Uncava did it (an outreach reply, a due date). */
  actor: TaskPerson | null;
  projectId: string | null;
  projectTitle: string | null;
  personId: string | null;
  candidateId: string | null;
  personName: string | null;
  taskId: string | null;
  taskTitle: string | null;
  /** The note it mentions the viewer in, or the note the assigned task came from. */
  note: TaskNote | null;
  /** A grouped row's latest people, newest first. */
  names: string[];
  dueAt: string | null;
  createdAt: string;
  read: boolean;
  snoozedUntil: string | null;
}

export type NotificationFilter = "unread" | "all";

export interface NotificationsResponse {
  items: NotificationResponse[];
  nextCursor: string | null;
  unreadCount: number;
}

export interface NotificationChange {
  read?: boolean;
  snoozedUntil?: string;
  clearSnooze?: boolean;
  archived?: boolean;
}

export type NotificationPreferenceKind =
  | "task_assigned"
  | "task_due"
  | "mentioned"
  | "outreach_replied"
  | "outreach_bounced"
  | "call_booked"
  | "added_to_team"
  | "tasks_moved"
  | "meeting_notes";

export interface NotificationPreferences {
  kinds: { kind: NotificationPreferenceKind; inApp: boolean; email: boolean }[];
  digest: boolean;
  /** Read-only: the connected mailbox's zone, else the workspace default the digest goes out in. */
  timeZone: string;
}
