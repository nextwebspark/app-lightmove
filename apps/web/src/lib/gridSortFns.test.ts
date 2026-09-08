import { describe, expect, it } from "vitest";
import { compareDate, compareNumber, compareText } from "./gridSortFns";

describe("grid comparators", () => {
  it("orders text by locale, so a capital does not jump the queue", () => {
    expect(["Zeta", "apple", "Beta"].sort(compareText)).toEqual(["apple", "Beta", "Zeta"]);
  });

  it("puts a blank after every value, whatever kind it is", () => {
    expect(["b", null, "a", undefined].sort(compareText)).toEqual(["a", "b", null, undefined]);
    expect([3, null, 1].sort(compareNumber)).toEqual([1, 3, null]);
    expect(["2026-09-15", null, "2026-08-07"].sort(compareDate)).toEqual([
      "2026-08-07",
      "2026-09-15",
      null,
    ]);
  });

  it("treats two blanks as equal, so a stable sort leaves them where they were", () => {
    expect(compareText(null, undefined)).toBe(0);
    expect(compareNumber(undefined, null)).toBe(0);
    expect(compareDate("", null)).toBe(0);
  });
});
