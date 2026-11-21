import type { TaskResponse } from "../../tasks/api/types";
import type { MyConversationRow } from "../api/homeApi";
import type { NeedsKind } from "./homeLayout";

/** One row of Needs you: an executive waiting in outreach, or a task of the caller's. */
export type NeedsItem =
  | { kind: "conversation"; key: string; group: NeedsKind; at: string | null; entry: MyConversationRow }
  | { kind: "task"; key: string; at: string | null; task: TaskResponse };

/**
 * A call or meeting still to come is today's agenda, drawn under Today rather than in the queue. One whose time has
 * passed is owed like any other task, so it stays in the queue: Today only lists what is still ahead.
 */
export function isAgendaTask(task: TaskResponse, now: Date = new Date()): boolean {
  return (task.kind === "call" || task.kind === "meeting") && task.dueAt !== null && new Date(task.dueAt) >= now;
}

/**
 * The queue: outreach rows in the groups the person keeps, then their tasks. A task a row already carries as its next
 * action is that row, so it is never drawn twice. Rows keep the order the server sent them in, the To do queue's own,
 * and tasks follow, soonest due first.
 */
export function needsYouOf(
  conversations: readonly MyConversationRow[],
  tasks: readonly TaskResponse[],
  kinds: Record<NeedsKind, boolean>,
  now: Date = new Date(),
): NeedsItem[] {
  const shown = conversations.filter((entry) => entry.row.group !== null && kinds[entry.row.group]);
  const carried = new Set(shown.map((entry) => entry.row.action?.taskId).filter(Boolean));
  const rows = shown.map((entry): NeedsItem => ({
    kind: "conversation",
    key: `c:${entry.projectId}:${entry.row.candidateId}`,
    group: entry.row.group as NeedsKind,
    at: entry.row.action?.dueAt ?? null,
    entry,
  }));
  const own = tasks
    .filter((task) => task.status === "open" && !carried.has(task.id) && !isAgendaTask(task, now))
    .sort((left, right) => compareAt(left.dueAt, right.dueAt))
    .map((task): NeedsItem => ({ kind: "task", key: `t:${task.id}`, at: task.dueAt, task }));
  return [...rows, ...own];
}

function compareAt(left: string | null, right: string | null): number {
  if (left === right) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return left < right ? -1 : 1;
}

