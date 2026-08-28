import { describe, expect, it } from "vitest";
import type { Compensation } from "../api/types";
import { annualisedAllowances, bandReadings, bonusOn, packageMix, packageTotal } from "./compensation";

const compensation = (overrides: Partial<Compensation> = {}): Compensation => ({
  currency: "AED",
  salaryMin: 90_000,
  salaryMax: 120_000,
  baseSalaryMode: "MONTHLY",
  bonusValue: null,
  bonusBasis: null,
  incentiveType: null,
  incentiveAmount: null,
  incentiveVesting: null,
  benefits: [],
  ...overrides,
});

describe("packageTotal", () => {
  it("annualises a monthly base", () => {
    const total = packageTotal(compensation());
    expect(total.base).toEqual({ min: 1_080_000, max: 1_440_000 });
  });

  it("leaves an annual base alone", () => {
    const total = packageTotal(compensation({ baseSalaryMode: "ANNUAL" }));
    expect(total.base).toEqual({ min: 90_000, max: 120_000 });
  });

  it("has no total at all until a band is entered", () => {
    const total = packageTotal(compensation({ salaryMin: null, salaryMax: null }));
    expect(total.min).toBeNull();
    expect(total.max).toBeNull();
  });

  it("adds bonus, incentive and annualised allowances to the band", () => {
    const total = packageTotal(
      compensation({
        bonusValue: 40,
        bonusBasis: "PERCENT_OF_BASE",
        incentiveAmount: 600_000,
        benefits: [
          { name: "Housing", amount: 8_000, frequency: "MONTHLY" },
          { name: "Schooling", amount: 30_000, frequency: "YEARLY" },
        ],
      }),
    );
    // 1,080,000 base + 432,000 bonus + 600,000 LTIP + 126,000 allowances
    expect(total.min).toBe(2_238_000);
  });
});

describe("bonusOn", () => {
  it("reads a percentage against the base", () => {
    expect(bonusOn(compensation({ bonusValue: 40, bonusBasis: "PERCENT_OF_BASE" }), 1_000_000))
      .toBe(400_000);
  });

  it("reads months of base as months, not as a percentage", () => {
    // Three months of a 1.2m annual base is 300,000 — the mockup's own script would have said 3.6%.
    expect(bonusOn(compensation({ bonusValue: 3, bonusBasis: "MONTHS_OF_BASE" }), 1_200_000))
      .toBe(300_000);
  });

  it("includes the allowances when the basis is total fixed", () => {
    const withAllowance = compensation({
      bonusValue: 10,
      bonusBasis: "PERCENT_OF_TOTAL_FIXED",
      benefits: [{ name: "Housing", amount: 10_000, frequency: "YEARLY" }],
    });
    expect(bonusOn(withAllowance, 1_000_000)).toBe(101_000);
  });

  it("contributes nothing while the basis is unset — a bare number means nothing yet", () => {
    expect(bonusOn(compensation({ bonusValue: 40, bonusBasis: null }), 1_000_000)).toBe(0);
  });
});

describe("annualisedAllowances", () => {
  it("multiplies monthly lines and takes yearly ones as they are", () => {
    expect(
      annualisedAllowances(
        compensation({
          benefits: [
            { name: "Housing", amount: 8_000, frequency: "MONTHLY" },
            { name: "Schooling", amount: 30_000, frequency: "YEARLY" },
            { name: "Home leave", amount: null, frequency: "YEARLY" },
          ],
        }),
      ),
    ).toBe(126_000);
  });
});

describe("packageMix", () => {
  it("shares out the top of the band, and never hides a component that has money in it", () => {
    const total = packageTotal(
      compensation({ bonusValue: 1, bonusBasis: "PERCENT_OF_BASE", incentiveAmount: 1 }),
    );
    const mix = packageMix(total);
    expect(mix.map((row) => row.label)).toEqual(["Base", "Bonus", "LTIP & allowances"]);
    expect(mix[0].percent).toBeGreaterThan(90);
    expect(mix[2].percent).toBe(1);
  });

  it("reads as nothing at all when there is no band", () => {
    expect(packageMix(packageTotal(compensation({ salaryMin: null, salaryMax: null })))
      .every((row) => row.percent === 0)).toBe(true);
  });
});

describe("bandReadings", () => {
  it("puts the midpoint between the bounds", () => {
    expect(bandReadings(compensation())).toEqual({ min: 90_000, mid: 105_000, max: 120_000 });
  });

  it("has no midpoint until both bounds are given", () => {
    expect(bandReadings(compensation({ salaryMax: null })).mid).toBeNull();
  });
});
