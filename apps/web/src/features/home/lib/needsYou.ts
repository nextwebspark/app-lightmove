import type { TaskKind, TaskPerson, TaskResponse } from "../../tasks/api/types";
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

/** A task as Needs you lists it on one line — the caller's own, or the next action an outreach row carries. */
export interface TaskLine {
  key: string;
  taskId: string;
  title: string;
  kind: TaskKind;
  dueAt: string | null;
  assignee: TaskPerson;
  projectId: string;
  projectTitle: string | null;
  candidateId: string | null;
  personId: string;
  personName: string;
  /** The outreach group that carried it, for its hover. */
  group: NeedsKind | null;
}

/** The two decisions outreach asks for in place: an answer to a reply, a delivery to put right. */
const CARD_GROUPS: ReadonlySet<NeedsKind> = new Set(["NEEDS_REPLY", "ATTENTION"]);

/**
 * Needs you's two blocks. A reply or a delivery problem is a card. A Follow-up or Before first email row is only
 * waiting on the task it carries, so it is that task's line, beside the caller's own, soonest due first.
 */
export function blocksOf(items: readonly NeedsItem[]): {
  cards: Extract<NeedsItem, { kind: "conversation" }>[];
  lines: TaskLine[];
} {
  const cards: Extract<NeedsItem, { kind: "conversation" }>[] = [];
  const lines: TaskLine[] = [];
  for (const item of items) {
    if (item.kind === "task") {
      const { task } = item;
      lines.push({
        key: item.key,
        taskId: task.id,
        title: task.title,
        kind: task.kind,
        dueAt: task.dueAt,
        assignee: task.assignee,
        projectId: task.projectId,
        projectTitle: task.projectTitle,
        candidateId: task.candidateId,
        personId: task.personId,
        personName: task.personName ?? "Someone",
        group: null,
      });
      continue;
    }
    const { row } = item.entry;
    if (CARD_GROUPS.has(item.group) || !row.action) {
      cards.push(item);
      continue;
    }
    lines.push({
      key: item.key,
      taskId: row.action.taskId,
      title: row.action.title,
      kind: row.action.kind,
      dueAt: row.action.dueAt,
      assignee: row.action.assignee,
      projectId: item.entry.projectId,
      projectTitle: item.entry.positionTitle,
      candidateId: row.candidateId,
      personId: row.personId,
      personName: row.fullName,
      group: item.group,
    });
  }
  lines.sort((left, right) => compareAt(left.dueAt, right.dueAt));
  return { cards, lines };
}

function compareAt(left: string | null, right: string | null): number {
  if (left === right) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return left < right ? -1 : 1;
}

