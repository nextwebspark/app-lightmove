import { describe, expect, it } from "vitest";
import type { TaskResponse } from "../api/types";
import { groupByUrgency } from "./taskUrgency";

const NOW = new Date(2026, 9, 14, 12, 0);

function task(id: string, dueAt: Date | null): TaskResponse {
  return { id, title: id, dueAt: dueAt?.toISOString() ?? null } as TaskResponse;
}

describe("groupByUrgency", () => {
  it("files tasks under Overdue, Today and Upcoming, in that order, keeping each group's order", () => {
    const groups = groupByUrgency(
      [
        task("late", new Date(2026, 9, 13, 9, 0)),
        task("earlier-today", new Date(2026, 9, 14, 9, 0)),
        task("this-afternoon", new Date(2026, 9, 14, 16, 0)),
        task("tonight", new Date(2026, 9, 14, 20, 0)),
        task("next-week", new Date(2026, 9, 21, 9, 0)),
        task("whenever", null),
      ],
      NOW,
    );

    expect(groups.map((group) => [group.label, group.tasks.map((t) => t.id)])).toEqual([
      ["Overdue", ["late", "earlier-today"]],
      ["Today", ["this-afternoon", "tonight"]],
      ["Upcoming", ["next-week", "whenever"]],
    ]);
  });

  it("leaves out a group with nothing in it", () => {
    expect(groupByUrgency([task("next-week", new Date(2026, 9, 21, 9, 0))], NOW).map((group) => group.label)).toEqual([
      "Upcoming",
    ]);
  });
});
