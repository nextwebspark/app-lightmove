import { describe, expect, it } from "vitest";
import {
  allowanceTotalOf,
  amountTyped,
  annualBaseOf,
  bonusAmountOf,
  bonusBasisOf,
  packageOf,
  toggleIncentiveType,
} from "./compensation";

describe("packageOf", () => {
  it("sums what is paid and shares it out, leaving the unpaid elements off the bar", () => {
    const { total, parts } = packageOf({
      baseSalary: 300_000,
      bonus: 100_000,
      allowances: null,
      longTermIncentive: 0,
    });
    expect(total).toBe(400_000);
    expect(parts.map((part) => [part.label, part.share])).toEqual([
      ["Base", 0.75],
      ["Bonus", 0.25],
    ]);
  });

  it("is an empty package when nothing is established", () => {
    expect(packageOf({ baseSalary: null, bonus: null, allowances: null, longTermIncentive: null })).toEqual({
      total: 0,
      parts: [],
    });
  });
});

describe("amountTyped", () => {
  it("reads a figure with or without its separators", () => {
    expect(amountTyped("420,000")).toBe(420_000);
    expect(amountTyped("420000")).toBe(420_000);
    expect(amountTyped("")).toBeNull();
    expect(amountTyped("lots")).toBeNull();
  });
});

describe("the editor's conversions", () => {
  it("stores a monthly base as the year it comes to", () => {
    expect(annualBaseOf(150_000, "monthly")).toBe(1_800_000);
    expect(annualBaseOf(150_000, "annual")).toBe(150_000);
    expect(annualBaseOf(null, "monthly")).toBeNull();
  });

  it("turns a share of base into the amount, and never invents one without a base", () => {
    expect(bonusAmountOf(1_800_000, 45, "percent")).toBe(810_000);
    expect(bonusAmountOf(1_800_000, 90_000, "fixed")).toBe(90_000);
    expect(bonusAmountOf(null, 45, "percent")).toBeNull();
  });

  it("reopens a bonus as a share unless there is no base to share it of", () => {
    expect(bonusBasisOf(420_000, 84_000)).toBe("percent");
    expect(bonusBasisOf(null, null)).toBe("percent");
    expect(bonusBasisOf(null, 84_000)).toBe("fixed");
  });

  it("sums the lines that hold a figure, and is nothing when none does", () => {
    expect(allowanceTotalOf([414_000, null, 138_000])).toBe(552_000);
    expect(allowanceTotalOf([null, null])).toBeNull();
  });

  it("keeps None on its own, and lands on it when the last instrument is let go", () => {
    expect(toggleIncentiveType(["options"], "none")).toEqual(["none"]);
    expect(toggleIncentiveType(["none"], "rsus")).toEqual(["rsus"]);
    expect(toggleIncentiveType(["options", "rsus"], "rsus")).toEqual(["options"]);
    // As the mockup's chips do: letting go of the last instrument lands on None.
    expect(toggleIncentiveType(["options"], "options")).toEqual(["none"]);
  });
});
