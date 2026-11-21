import { afterEach, describe, expect, it } from "vitest";
import { DEFAULT_HOME_LAYOUT, landingPath, moveSection, readHomeLayout } from "./homeLayout";

const owner = { userId: "u1", workspaceId: "w1" };

describe("the Home layout", () => {
  afterEach(() => localStorage.clear());

  it("is the default until the person changes it", () => {
    expect(readHomeLayout(owner)).toEqual(DEFAULT_HOME_LAYOUT);
    expect(landingPath(owner)).toBe("/");
  });

  it("fills in what a stored layout is missing or got wrong from the default", () => {
    localStorage.setItem(
      "lm-home:u1:w1",
      JSON.stringify({ sections: [{ key: "charts", on: false }, { key: "nonsense", on: true }], kinds: { FOLLOW_UP: false }, landing: "positions" }),
    );
    const layout = readHomeLayout(owner);
    expect(layout.sections.map((section) => section.key)).toEqual(["charts", "today", "positions"]);
    expect(layout.sections[0].on).toBe(false);
    expect(layout.kinds.FOLLOW_UP).toBe(false);
    expect(layout.kinds.NEEDS_REPLY).toBe(true);
    expect(landingPath(owner)).toBe("/positions");
  });

  it("is each person's own, in each workspace", () => {
    localStorage.setItem("lm-home:u1:w1", JSON.stringify({ landing: "positions" }));
    expect(landingPath({ userId: "u1", workspaceId: "w2" })).toBe("/");
    expect(landingPath({ userId: "u2", workspaceId: "w1" })).toBe("/");
  });

  it("reads an unreadable layout as the default", () => {
    localStorage.setItem("lm-home:u1:w1", "{not json");
    expect(readHomeLayout(owner)).toEqual(DEFAULT_HOME_LAYOUT);
  });

  it("moves a section within its bounds", () => {
    expect(moveSection(DEFAULT_HOME_LAYOUT, "positions", -1).sections.map((section) => section.key)).toEqual([
      "positions",
      "today",
      "charts",
    ]);
    expect(moveSection(DEFAULT_HOME_LAYOUT, "today", -1)).toBe(DEFAULT_HOME_LAYOUT);
  });
});
