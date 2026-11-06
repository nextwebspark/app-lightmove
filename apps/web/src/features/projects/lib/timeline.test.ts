import { describe, expect, it } from "vitest";
import { daysBetween, todayIso } from "./timeline";

describe("timeline", () => {
  it("counts whole days across a month end", () => {
    expect(daysBetween("2026-09-24", "2026-10-15")).toBe(21);
  });

  it("reads today from the local calendar", () => {
    expect(todayIso(new Date(2026, 8, 24, 23, 30))).toBe("2026-09-24");
  });
});
