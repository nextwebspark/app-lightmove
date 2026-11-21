import { describe, expect, it } from "vitest";
import { MY_TASKS_KEY, TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import { DEFAULT_HOME_LAYOUT } from "../lib/homeLayout";
import { HOME_TASKS_KEY, toHomeLayout, toHomeLayoutBody } from "./homeApi";

describe("Home's task keys", () => {
  // My tasks writes a tick into every entry under ["tasks", "mine"] as pages of an infinite list; Home's plain answers
  // there made every tick on /tasks fail once Home had been opened.
  it("sit apart from My tasks' lists, and under every task write's invalidation", () => {
    const mine = MY_TASKS_KEY("today").slice(0, 2);
    expect(HOME_TASKS_KEY("today").slice(0, 2)).not.toEqual(mine);
    expect(HOME_TASKS_KEY("today").slice(0, TASKS_KEY_PREFIX.length)).toEqual([...TASKS_KEY_PREFIX]);
  });
});

describe("Home's layout on the wire", () => {
  it("survives the trip to the server and back, landing and Needs you groups in the server's words", () => {
    const body = toHomeLayoutBody(DEFAULT_HOME_LAYOUT);
    expect(body.landing).toBe("HOME");
    expect(body.tiles[0]).toEqual({ kind: "NEEDS", layout: { x: 0, y: 0, w: 8, h: 11 } });
    expect(toHomeLayout(body)).toEqual(DEFAULT_HOME_LAYOUT);
    expect(toHomeLayout({ ...body, needs: { ATTENTION: false }, landing: "POSITIONS" })).toMatchObject({
      kinds: { NEEDS_REPLY: true, ATTENTION: false },
      landing: "positions",
    });
  });
});
