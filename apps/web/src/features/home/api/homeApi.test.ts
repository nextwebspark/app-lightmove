import { describe, expect, it } from "vitest";
import { MY_TASKS_KEY, TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import { HOME_TASKS_KEY } from "./homeApi";

describe("Home's task keys", () => {
  // My tasks writes a tick into every entry under ["tasks", "mine"] as pages of an infinite list; Home's plain answers
  // there made every tick on /tasks fail once Home had been opened.
  it("sit apart from My tasks' lists, and under every task write's invalidation", () => {
    const mine = MY_TASKS_KEY("today").slice(0, 2);
    expect(HOME_TASKS_KEY("today").slice(0, 2)).not.toEqual(mine);
    expect(HOME_TASKS_KEY("today").slice(0, TASKS_KEY_PREFIX.length)).toEqual([...TASKS_KEY_PREFIX]);
  });
});
