import type { PersonNoteKind } from "../../candidates/api/types";

/** A job on one executive of one position — the card's Next action is the earliest open one. Staff-only. */
export type TaskKind = "call" | "email" | "meeting" | "follow_up" | "todo";

export type TaskStatus = "open" | "done" | "cancelled";

/** What outreach event filed the task, when a rule did rather than a person. */
export type TaskRule = "reply" | "bounce" | "booked_call";

export type TaskSource = "manual" | "note" | "rule";

/** A teammate as a task names them: always drawn with their avatar. */
export interface TaskPerson {
  userId: string;
  fullName: string | null;
  avatarUrl: string | null;
}

/** A note's kind as the server names it: a person's note's own, by construction. */
export type TaskNoteKind = PersonNoteKind;

/** The note a task or a bell row came from, named by its kind and date — never by what it says. */
export interface TaskNote {
  id: string;
  kind: TaskNoteKind;
  writtenAt: string;
}

export interface TaskResponse {
  id: string;
  projectId: string;
  projectTitle: string | null;
  candidateId: string | null;
  personId: string;
  personName: string | null;
  title: string;
  kind: TaskKind;
  detail: string | null;
  presetId: string | null;
  /** The note it was set from; null once that note is deleted. */
  note: TaskNote | null;
  assignee: TaskPerson;
  /** Null when a rule filed it. */
  createdBy: TaskPerson | null;
  dueAt: string | null;
  status: TaskStatus;
  source: TaskSource;
  rule: TaskRule | null;
  completedAt: string | null;
  completedBy: TaskPerson | null;
  createdAt: string;
  /** Whether the caller may change, reassign or cancel it; completing it is anyone's on the team. */
  editable: boolean;
}

/**
 * One executive's tasks on one position: every open one, soonest first, and the done ones latest first — the last
 * three, or every one when asked. `doneCount` is how many are done in all.
 */
export interface TasksResponse {
  open: TaskResponse[];
  done: TaskResponse[];
  doneCount: number;
}

export interface CreateTaskRequest {
  title: string;
  kind: TaskKind;
  assigneeUserId: string;
  dueAt: string;
  detail?: string | null;
  presetId?: string | null;
  noteId?: string | null;
}

export interface UpdateTaskRequest {
  title?: string;
  kind?: TaskKind;
  assigneeUserId?: string;
  dueAt?: string;
  detail?: string | null;
  status?: "open" | "done";
}

export type MyTasksView = "today" | "overdue" | "upcoming" | "assigned_by_me";

export interface MyTasksResponse {
  tasks: TaskResponse[];
  counts: { today: number; overdue: number; upcoming: number; assignedByMe: number };
  /** The tab holds more after this slice. */
  hasMore: boolean;
}

/** The workspace's catalog a next action is picked from; free text is a title only and never lands here. */
export interface TaskPreset {
  id: string;
  label: string;
  kind: TaskKind;
  /** Days from today the due date defaults to; 0 is the same day, null leaves it to the person. */
  dueOffsetDays: number | null;
  position: number;
  retired: boolean;
  retiredAt: string | null;
  usage: number;
}

export interface TaskPresetDraft {
  label: string;
  kind: TaskKind;
  dueOffsetDays: number | null;
}

export interface TaskPresetChange {
  label?: string;
  kind?: TaskKind;
  dueOffsetDays?: number | null;
  clearDueOffset?: boolean;
  retired?: boolean;
}

/** A free-text title the team keeps typing — Settings offers to make it a preset. */
export interface TaskPresetSuggestion {
  label: string;
  count: number;
}

/** Which outreach events file a task by themselves, per workspace. */
export interface TaskRules {
  reply: boolean;
  bounce: boolean;
  bookedCall: boolean;
}
