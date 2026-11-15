import { describe, expect, it } from "vitest";
import type { StrategyFilter } from "../api/types";
import { appliedRange, isInverted, savableFilter } from "./filterRanges";

const FILTER: StrategyFilter = {
  industries: ["retail"],
  keywords: [],
  marketSegments: [],
  countries: ["United Arab Emirates"],
  employeeBands: [],
  revenueBands: [],
  employeeRange: null,
  revenueRange: null,
};

describe("isInverted", () => {
  it("is only a range whose typed minimum is above its typed maximum", () => {
    expect(isInverted({ min: 500, max: 100 })).toBe(true);
    expect(isInverted({ min: 100, max: 100 })).toBe(false);
    expect(isInverted({ min: 100, max: 500 })).toBe(false);
    expect(isInverted({ min: 500, max: null })).toBe(false);
    expect(isInverted({ min: null, max: 100 })).toBe(false);
    expect(isInverted(null)).toBe(false);
  });
});

describe("appliedRange", () => {
  it("is the range only when it narrows the scope", () => {
    expect(appliedRange({ min: 10, max: 50 })).toEqual({ min: 10, max: 50 });
    expect(appliedRange({ min: 10, max: null })).toEqual({ min: 10, max: null });
    expect(appliedRange({ min: null, max: 50 })).toEqual({ min: null, max: 50 });
    expect(appliedRange({ min: null, max: null })).toBeNull();
    expect(appliedRange({ min: 50, max: 10 })).toBeNull();
    expect(appliedRange(null)).toBeNull();
  });
});

describe("savableFilter", () => {
  it("sends an inverted range as no range, keeping every other axis", () => {
    const saved = savableFilter({ ...FILTER, employeeRange: { min: 500, max: 100 } });
    expect(saved.employeeRange).toBeNull();
    expect(saved.industries).toEqual(["retail"]);
    expect(saved.countries).toEqual(["United Arab Emirates"]);
  });

  it("leaves the other axis's valid range alone", () => {
    const saved = savableFilter({
      ...FILTER,
      employeeRange: { min: 10, max: 50 },
      revenueRange: { min: 9_000_000, max: 1_000_000 },
    });
    expect(saved.employeeRange).toEqual({ min: 10, max: 50 });
    expect(saved.revenueRange).toBeNull();
  });

  it("hands back the same filter when nothing is inverted", () => {
    const filter = { ...FILTER, revenueRange: { min: 1, max: null } };
    expect(savableFilter(filter)).toBe(filter);
  });
});
