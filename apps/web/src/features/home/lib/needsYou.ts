import type { TaskResponse } from "../../tasks/api/types";
import type { MyConversationRow } from "../api/homeApi";
import type { NeedsKind } from "./homeLayout";

/** One row of Needs you: an executive waiting in outreach, or a task of the caller's. */
export type NeedsItem =
  | { kind: "conversation"; key: string; group: NeedsKind; at: string | null; entry: MyConversationRow }
  | { kind: "task"; key: string; at: string | null; task: TaskResponse };

const GROUP_RANK: Record<NeedsKind, number> = { NEEDS_REPLY: 0, ATTENTION: 1, FOLLOW_UP: 2, PREPARATION: 3 };

/** A timed call or meeting is today's agenda, drawn under Today rather than in the queue. */
export function isAgendaTask(task: TaskResponse): boolean {
  return task.kind === "call" || task.kind === "meeting";
}

/**
 * The queue: outreach rows in the groups the person keeps, then their overdue and today's tasks. A task a row already
 * carries as its next action is that row, so it is never drawn twice; a reply comes first, then whatever is oldest.
 */
export function needsYouOf(
  conversations: readonly MyConversationRow[],
  tasks: readonly TaskResponse[],
  kinds: Record<NeedsKind, boolean>,
): NeedsItem[] {
  const shown = conversations.filter((entry) => entry.row.group !== null && kinds[entry.row.group]);
  const carried = new Set(shown.map((entry) => entry.row.action?.taskId).filter(Boolean));
  const rows: NeedsItem[] = shown.map((entry) => ({
    kind: "conversation",
    key: `c:${entry.projectId}:${entry.row.candidateId}`,
    group: entry.row.group as NeedsKind,
    at: entry.row.action?.dueAt ?? entry.row.lastActivityAt,
    entry,
  }));
  rows.sort((left, right) => {
    if (left.kind !== "conversation" || right.kind !== "conversation") return 0;
    return GROUP_RANK[left.group] - GROUP_RANK[right.group] || compareAt(left.at, right.at);
  });
  const own = tasks
    .filter((task) => task.status === "open" && !carried.has(task.id) && !isAgendaTask(task))
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
