import { describe, expect, it } from "vitest";
import {
  allowanceTotalOf,
  amountTyped,
  annualBaseOf,
  bonusAmountOf,
  bonusBasisOf,
  expectedPackageOf,
  incentiveTypesFor,
  mobilityOf,
  packageOf,
  shareTyped,
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

  it("reopens a bonus as a share only where the share comes back to the stored amount", () => {
    expect(bonusBasisOf(420_000, 84_000)).toBe("percent");
    expect(bonusBasisOf(null, null)).toBe("percent");
    expect(bonusBasisOf(null, 84_000)).toBe("fixed");
    // 19.8% of 420,000 is 83,160: reopening this as a share would rewrite it on the next save.
    expect(bonusBasisOf(420_000, 83_333)).toBe("fixed");
  });

  it("drops None beside an LTIP amount, and keeps it where there is none", () => {
    expect(incentiveTypesFor(1_000_000, ["none"])).toEqual([]);
    expect(incentiveTypesFor(null, ["none"])).toEqual(["none"]);
  });

  it("reads a share with its percent sign, and an amount without one", () => {
    expect(shareTyped("45%")).toBe(45);
    expect(amountTyped("45%")).toBeNull();
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

describe("expectedPackageOf", () => {
  it("reads the total first in the package's currency, then the base and their own words", () => {
    expect(
      expectedPackageOf("AED", {
        expectedTotalPackage: 4_000_000,
        expectedBaseSalary: 1_700_000,
        expectedPackageNote: "Wants the LTIP to carry over",
      }),
    ).toBe("AED 4,000,000 total · base 1,700,000 · Wants the LTIP to carry over");
  });

  it("gives the base the currency when no total was stated, and keeps a stated nought", () => {
    expect(expectedPackageOf("AED", { expectedTotalPackage: null, expectedBaseSalary: 0, expectedPackageNote: null }))
      .toBe("AED 0 base");
  });

  it("is their words alone with no figure, and nothing at all when nobody asked", () => {
    expect(
      expectedPackageOf("AED", { expectedTotalPackage: null, expectedBaseSalary: null, expectedPackageNote: "Open" }),
    ).toBe("Open");
    expect(
      expectedPackageOf("AED", { expectedTotalPackage: null, expectedBaseSalary: null, expectedPackageNote: null }),
    ).toBeNull();
  });
});

describe("mobilityOf", () => {
  it("names how far they would move, then what would make it work", () => {
    expect(mobilityOf({ mobility: "region", mobilityNote: "GCC; family stays in Abu Dhabi" })).toBe(
      "Open within the region · GCC; family stays in Abu Dhabi",
    );
  });

  it("keeps a recorded no apart from nobody having asked", () => {
    expect(mobilityOf({ mobility: "none", mobilityNote: null })).toBe("Not open to relocating");
    expect(mobilityOf({ mobility: null, mobilityNote: null })).toBeNull();
  });
});
