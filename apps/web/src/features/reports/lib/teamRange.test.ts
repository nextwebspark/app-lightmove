import { describe, expect, it } from "vitest";
import { teamRangeOf } from "./teamRange";

describe("teamRangeOf", () => {
  // 2026-09-08 is a Tuesday.
  const asOf = "2026-09-08";

  it("counts the rolling ranges back from the report's own today, inclusive", () => {
    expect(teamRangeOf("7d", asOf, {})).toEqual({ from: "2026-09-02", to: asOf });
    expect(teamRangeOf("30d", asOf, {})).toEqual({ from: "2026-08-10", to: asOf });
  });

  it("starts this week on the Sunday of the GCC working week", () => {
    expect(teamRangeOf("week", asOf, {})).toEqual({ from: "2026-09-06", to: asOf });
    expect(teamRangeOf("week", "2026-09-06", {})).toEqual({ from: "2026-09-06", to: "2026-09-06" });
  });

  it("asks for no bounds on all time, and passes a custom range through", () => {
    expect(teamRangeOf("all", asOf, {})).toEqual({});
    expect(teamRangeOf("custom", asOf, { from: "2026-08-01" })).toEqual({ from: "2026-08-01" });
  });
});
