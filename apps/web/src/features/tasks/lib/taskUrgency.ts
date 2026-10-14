import { describeDue } from "../../../lib/format";
import type { TaskResponse } from "../api/types";

export type TaskUrgency = "overdue" | "today" | "upcoming";

export interface TaskUrgencyGroup {
  urgency: TaskUrgency;
  label: string;
  tasks: TaskResponse[];
}

const GROUPS: readonly { urgency: TaskUrgency; label: string }[] = [
  { urgency: "overdue", label: "Overdue" },
  { urgency: "today", label: "Today" },
  { urgency: "upcoming", label: "Upcoming" },
];

/** Open tasks in the groups a reader triages by, keeping the server's soonest-first order within each. */
export function groupByUrgency(tasks: readonly TaskResponse[], now: Date = new Date()): TaskUrgencyGroup[] {
  const urgencyOf = (task: TaskResponse): TaskUrgency => {
    const tone = describeDue(task.dueAt, now)?.tone;
    return tone === "overdue" ? "overdue" : tone === "today" ? "today" : "upcoming";
  };
  return GROUPS.map((group) => ({ ...group, tasks: tasks.filter((task) => urgencyOf(task) === group.urgency) })).filter(
    (group) => group.tasks.length > 0,
  );
}
