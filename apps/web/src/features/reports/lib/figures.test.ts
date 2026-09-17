import { describe, expect, it } from "vitest";
import { addDays, daysBetween, formatCompactMoney, formatShortDate, ordinal, percent } from "./figures";

/** The vocabulary every chapter states a figure in — one rounding rule, one date arithmetic. */
describe("figures", () => {
  it("states money in the tier a finding reads, dropping a trailing .0", () => {
    expect(formatCompactMoney("USD", 1_100_000)).toBe("USD 1.1M");
    expect(formatCompactMoney("USD", 1_000_000)).toBe("USD 1M");
    expect(formatCompactMoney("USD", 12_400_000)).toBe("USD 12M");
    expect(formatCompactMoney("AED", 320_000)).toBe("AED 320K");
    expect(formatCompactMoney("USD", 950)).toBe("USD 950");
  });

  it("gives every number its ordinal, the teens included", () => {
    expect([1, 2, 3, 4, 11, 12, 13, 21, 38, 112].map(ordinal)).toEqual([
      "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "38th", "112th",
    ]);
  });

  it("answers zero rather than NaN for a share of nothing", () => {
    expect(percent(0, 0)).toBe(0);
    expect(percent(37, 114)).toBe(32);
  });

  it("counts and adds days across a month end, signed", () => {
    expect(daysBetween("2026-08-30", "2026-09-02")).toBe(3);
    expect(daysBetween("2026-09-02", "2026-08-30")).toBe(-3);
    expect(addDays("2026-08-30", 3)).toBe("2026-09-02");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
    expect(formatShortDate("2026-07-21")).toBe("21 Jul");
  });
});
