import { describe, expect, it } from "vitest";
import {
  noticePairLabel,
  noticePeriodOfPair,
  noticeSummaryOf,
  NOTICE_PERIODS,
  pairOfNoticePeriod,
} from "./noticePeriod";

describe("noticePeriod", () => {
  it("reads a stored pair as the option it names, in whatever unit it was stated", () => {
    expect(noticePeriodOfPair(3, "MONTHS")).toBe("3 months");
    expect(noticePeriodOfPair(90, "DAYS")).toBe("3 months");
    expect(noticePeriodOfPair(26, "WEEKS")).toBe("6 months");
    expect(noticePeriodOfPair(0, "MONTHS")).toBe("None");
  });

  it("reads a pair nobody offers as nothing, so the picker keeps it as recorded", () => {
    expect(noticePeriodOfPair(6, "WEEKS")).toBeNull();
    expect(noticePeriodOfPair(45, "DAYS")).toBeNull();
    expect(noticePeriodOfPair(null, null)).toBeNull();
    expect(noticePeriodOfPair(3, null)).toBeNull();
    expect(noticePairLabel(6, "WEEKS")).toBe("6 weeks");
  });

  it("writes every offered period back as a pair the brief can store", () => {
    for (const period of NOTICE_PERIODS) {
      const pair = pairOfNoticePeriod(period.label);
      expect(pair).toEqual({ noticeValue: period.months, noticeUnit: "MONTHS" });
      expect(noticePeriodOfPair(pair!.noticeValue, pair!.noticeUnit)).toBe(period.label);
    }
    expect(pairOfNoticePeriod("6 weeks")).toBeNull();
  });

  it("summarises None as a sentence rather than 'None notice'", () => {
    expect(noticeSummaryOf("None")).toBe("No notice period");
    expect(noticeSummaryOf("3 months")).toBe("3 months notice");
    expect(noticeSummaryOf(null)).toBeNull();
  });
});
