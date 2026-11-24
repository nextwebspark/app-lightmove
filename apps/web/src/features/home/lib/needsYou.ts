import { dayKeyOf, daysBetween } from "../../../lib/datetime/zone";
import type { ConversationGroup } from "../../outreach/api/conversationApi";
import type { TaskKind, TaskPerson, TaskResponse } from "../../tasks/api/types";
import type { MyConversationRow } from "../api/homeApi";

/** One row of the queue as the server and the caller's tasks give it: an executive waiting in outreach, or a task. */
export type NeedsItem =
  | { kind: "conversation"; key: string; group: ConversationGroup; entry: MyConversationRow }
  | { kind: "task"; key: string; task: TaskResponse };

/**
 * A call or meeting of the caller's own, later today, is drawn under Today rather than in the queue — exactly what
 * Today lists. Anything else stays in the queue: a call whose time has passed is owed like any other task, and a call
 * on another day, or a teammate's under Team, has no other place on Home.
 */
export function isTodayAgendaOf(
  task: Pick<TaskResponse, "kind" | "dueAt" | "assignee">,
  myUserId: string | undefined,
  now: Date = new Date(),
): boolean {
  if ((task.kind !== "call" && task.kind !== "meeting") || task.dueAt === null) return false;
  const due = new Date(task.dueAt);
  return task.assignee.userId === myUserId && due >= now && dayKeyOf(due) === dayKeyOf(now);
}

/**
 * The queue: every outreach row, then the caller's open tasks, less what Today draws. A task a row already carries as
 * its next action is that row, so it is never drawn twice.
 */
export function needsYouOf(
  conversations: readonly MyConversationRow[],
  tasks: readonly TaskResponse[],
  myUserId: string | undefined,
  now: Date = new Date(),
): NeedsItem[] {
  const shown = conversations.filter(
    (entry) =>
      entry.row.group !== null &&
      !(
        (entry.row.group === "FOLLOW_UP" || entry.row.group === "PREPARATION") &&
        entry.row.action &&
        isTodayAgendaOf(entry.row.action, myUserId, now)
      ),
  );
  const carried = new Set(shown.map((entry) => entry.row.action?.taskId).filter(Boolean));
  const rows = shown.map((entry): NeedsItem => ({
    kind: "conversation",
    key: `c:${entry.projectId}:${entry.row.candidateId}`,
    group: entry.row.group as ConversationGroup,
    entry,
  }));
  const own = tasks
    .filter((task) => task.status === "open" && !carried.has(task.id) && !isTodayAgendaOf(task, myUserId, now))
    .map((task): NeedsItem => ({ kind: "task", key: `t:${task.id}`, task }));
  return [...rows, ...own];
}

/** Why a line is on the list, most pressing first: it decides the order, the icon's tint and the chip. */
export type AttentionReason = "overdue" | "reply" | "delivery" | "today" | "later";

/** One line of Needs your attention: what to do, for whom, on which position, and how pressing it is. */
export interface AttentionLine {
  key: string;
  reason: AttentionReason;
  /** A task's own kind; null for a reply or a delivery problem, which is no task. */
  taskKind: TaskKind | null;
  /** The outreach group that put it here; null for one of the caller's own tasks. */
  group: ConversationGroup | null;
  /** A task's title; null where the line is worded from the person ("… replied to your outreach"). */
  taskTitle: string | null;
  /** When it was due, or when the executive last wrote. */
  at: string | null;
  projectId: string;
  positionTitle: string | null;
  candidateId: string | null;
  personId: string;
  personName: string;
  assignee: TaskPerson | null;
}

const REASON_ORDER: Record<AttentionReason, number> = { overdue: 0, reply: 1, delivery: 2, today: 3, later: 4 };

/** What Home lists, and how many tasks it leaves to My tasks because they are not due yet. */
export interface Attention {
  lines: AttentionLine[];
  comingUp: number;
}

/**
 * Needs your attention, in one list of what to act on now: overdue first (longest late at the top), then replies and
 * delivery problems (newest first), then what is due today (soonest first). A task due tomorrow or later is not on it,
 * whether it is the caller's own or a follow-up an executive's outreach row carries; it is only counted.
 */
export function attentionOf(items: readonly NeedsItem[], now: Date = new Date()): Attention {
  const today = dayKeyOf(now);
  const reasonOfDue = (dueAt: string | null): AttentionReason => {
    if (!dueAt) return "later";
    const due = new Date(dueAt);
    if (due.getTime() < now.getTime()) return "overdue";
    return daysBetween(today, dayKeyOf(due)) === 0 ? "today" : "later";
  };
  const lines = items.map((item): AttentionLine => {
    if (item.kind === "task") {
      const { task } = item;
      return {
        key: item.key,
        reason: reasonOfDue(task.dueAt),
        taskKind: task.kind,
        group: null,
        taskTitle: task.title,
        at: task.dueAt,
        projectId: task.projectId,
        positionTitle: task.projectTitle,
        candidateId: task.candidateId,
        personId: task.personId,
        personName: task.personName ?? "Someone",
        assignee: task.assignee,
      };
    }
    const { entry, group } = item;
    const { row } = entry;
    const base = {
      key: item.key,
      group,
      projectId: entry.projectId,
      positionTitle: entry.positionTitle,
      candidateId: row.candidateId,
      personId: row.personId,
      personName: row.fullName,
      assignee: row.action?.assignee ?? null,
    };
    if (group === "NEEDS_REPLY" || group === "ATTENTION" || !row.action) {
      return {
        ...base,
        reason: group === "NEEDS_REPLY" ? "reply" : group === "ATTENTION" ? "delivery" : "later",
        taskKind: null,
        taskTitle: null,
        at: row.latest?.at ?? row.lastActivityAt,
      };
    }
    return {
      ...base,
      reason: reasonOfDue(row.action.dueAt),
      taskKind: row.action.kind,
      taskTitle: row.action.title,
      at: row.action.dueAt,
    };
  });
  const sorted = lines.sort((left, right) => {
    const byReason = REASON_ORDER[left.reason] - REASON_ORDER[right.reason];
    if (byReason !== 0) return byReason;
    if (left.at === right.at) return 0;
    if (left.at === null) return 1;
    if (right.at === null) return -1;
    const earlierFirst = left.at < right.at ? -1 : 1;
    return left.reason === "reply" || left.reason === "delivery" ? -earlierFirst : earlierFirst;
  });
  const due = sorted.filter((line) => line.reason !== "later");
  return { lines: due, comingUp: sorted.length - due.length };
}
