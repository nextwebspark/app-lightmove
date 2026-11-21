import { afterEach, describe, expect, it } from "vitest";
import { DEFAULT_HOME_LAYOUT, landingPath, moveSection, readHomeLayout } from "./homeLayout";

describe("the Home layout", () => {
  afterEach(() => localStorage.clear());

  it("is the default until the person changes it", () => {
    expect(readHomeLayout()).toEqual(DEFAULT_HOME_LAYOUT);
    expect(landingPath()).toBe("/");
  });

  it("fills in what a stored layout is missing or got wrong from the default", () => {
    localStorage.setItem(
      "lm-home",
      JSON.stringify({ sections: [{ key: "charts", on: false }, { key: "nonsense", on: true }], kinds: { FOLLOW_UP: false }, landing: "positions" }),
    );
    const layout = readHomeLayout();
    expect(layout.sections.map((section) => section.key)).toEqual(["charts", "today", "positions"]);
    expect(layout.sections[0].on).toBe(false);
    expect(layout.kinds.FOLLOW_UP).toBe(false);
    expect(layout.kinds.NEEDS_REPLY).toBe(true);
    expect(landingPath()).toBe("/positions");
  });

  it("reads an unreadable layout as the default", () => {
    localStorage.setItem("lm-home", "{not json");
    expect(readHomeLayout()).toEqual(DEFAULT_HOME_LAYOUT);
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
