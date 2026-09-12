import { describe, expect, it } from "vitest";
import { amountTyped, packageOf } from "./compensation";

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
