import { daysBetween } from "../../../lib/datetime/zone";
import { formatShortDate, formatWeekdayDate } from "../../../lib/datetime/format";
import type { Project, ProjectStage } from "../../projects/api/types";
import { STAGE_ORDER } from "../../projects/lib/filtering";

/** A position's next dated step, as Home's table says it: what, and when — or how late. */
export interface Milestone {
  label: string;
  when: string;
  overdue: boolean;
}

const reached = (stage: ProjectStage, step: ProjectStage) => STAGE_ORDER.indexOf(stage) >= STAGE_ORDER.indexOf(step);

/**
 * The first of the position's dates not yet met: the universe mapped by its mapping date (met once outreach is
 * live), the map or shortlist by its delivery date (met once delivered), then the target start, while it is ahead.
 * A date passed and not met is overdue. Null when the position has no date still in play.
 */
export function nextMilestoneOf(project: Project, today: string): Milestone | null {
  const steps: { date: string | null; label: string; met: boolean; lapses: boolean }[] = [
    {
      date: project.mappingTargetDate,
      label: "Universe mapped",
      met: reached(project.stage, "OUTREACH"),
      lapses: true,
    },
    {
      date: project.deliveryDate,
      label: project.projectType === "MAPPING" ? "Map delivered" : "Shortlist delivered",
      met: reached(project.stage, "DELIVERED"),
      lapses: true,
    },
    { date: project.targetDate, label: "Target start", met: reached(project.stage, "CLOSED"), lapses: false },
  ];
  for (const step of steps) {
    if (!step.date || step.met) continue;
    const days = daysBetween(today, step.date);
    if (days < 0) {
      if (!step.lapses) continue;
      return { label: `${step.label} overdue`, when: `Was due ${formatShortDate(step.date)}`, overdue: true };
    }
    const when = days === 0 ? "Today" : days === 1 ? "Tomorrow" : (formatWeekdayDate(step.date) ?? step.date);
    return { label: step.label, when, overdue: false };
  }
  return null;
}
