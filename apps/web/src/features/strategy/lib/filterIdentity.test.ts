import { describe, expect, it } from "vitest";
import type { StrategyFilter } from "../api/types";
import { sameFilter } from "./filterIdentity";

const EMPTY: StrategyFilter = {
  industries: [],
  keywords: [],
  marketSegments: [],
  countries: [],
  employeeBands: [],
  revenueBands: [],
  employeeRange: null,
  revenueRange: null,
};

describe("sameFilter", () => {
  it("ignores the order chips were clicked in", () => {
    expect(
      sameFilter(
        { ...EMPTY, industries: ["utilities", "oil & energy"] },
        { ...EMPTY, industries: ["oil & energy", "utilities"] },
      ),
    ).toBe(true);
  });

  it("treats an unbounded custom range as no range at all", () => {
    // Entering Custom Range emits { min: null, max: null }, which the server normalises away. A
    // loaded search would otherwise stop looking active the moment the panel was opened.
    expect(
      sameFilter({ ...EMPTY, employeeRange: { min: null, max: null } }, EMPTY),
    ).toBe(true);
  });

  it("separates filters that differ on one axis", () => {
    expect(sameFilter({ ...EMPTY, countries: ["Qatar"] }, EMPTY)).toBe(false);
    expect(
      sameFilter(
        { ...EMPTY, revenueRange: { min: 500, max: null } },
        { ...EMPTY, revenueRange: { min: 500, max: 1000 } },
      ),
    ).toBe(false);
  });
});
