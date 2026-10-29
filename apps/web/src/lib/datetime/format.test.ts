import { afterEach, describe, expect, it } from "vitest";
import {
  formatClock,
  formatDate,
  formatFull,
  formatInstantDateTime,
  formatInstantTime,
  formatMonthYear,
  formatShortDate,
  formatWeekdayDate,
} from "./format";
import { setDateTimePreferences, type DateFormatStyle } from "./preferences";
import { dayKeyOf } from "./zone";

const MOMENT = "2026-12-04T10:30:00Z";

afterEach(() => setDateTimePreferences(null));

describe("every date the app draws, in the person's zone and formats", () => {
  it.each<[DateFormatStyle, string, string, string]>([
    ["DD_MMM_YYYY", "04 Dec 2026", "4 Dec", "Fri 4 Dec"],
    ["MMM_D_YYYY", "Dec 4, 2026", "Dec 4", "Fri, Dec 4"],
    ["DD_MM_YYYY", "04/12/2026", "4 Dec", "Fri 4 Dec"],
    ["MM_DD_YYYY", "12/04/2026", "Dec 4", "Fri, Dec 4"],
    ["YYYY_MM_DD", "2026-12-04", "4 Dec", "Fri 4 Dec"],
  ])("%s reads %s, and its shorter forms follow its order", (dateFormat, full, short, weekday) => {
    setDateTimePreferences({ timeZone: "Asia/Dubai", dateFormat, timeFormat: "H24" });

    expect(formatDate(MOMENT)).toBe(full);
    expect(formatShortDate(MOMENT)).toBe(short);
    expect(formatWeekdayDate(MOMENT)).toBe(weekday);
    expect(formatMonthYear(MOMENT)).toBe("Dec 2026");
  });

  it("reads the clock in 24 or 12 hours", () => {
    setDateTimePreferences({ timeZone: "Asia/Dubai", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" });
    expect(formatInstantDateTime(MOMENT)).toBe("04 Dec 2026, 14:30");
    expect(formatClock("09:05:00")).toBe("09:05");

    setDateTimePreferences({ timeZone: "Asia/Dubai", dateFormat: "MMM_D_YYYY", timeFormat: "H12" });
    expect(formatInstantDateTime(MOMENT)).toBe("Dec 4, 2026, 2:30 PM");
    expect(formatClock("00:15")).toBe("12:15 AM");
    expect(formatClock("12:00")).toBe("12:00 PM");
  });

  it("reads an instant in the chosen zone, not the computer's, the day included", () => {
    setDateTimePreferences({ timeZone: "America/New_York", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" });
    const lateInDubai = "2026-12-04T22:30:00Z";

    expect(formatInstantTime(lateInDubai)).toBe("17:30");
    expect(dayKeyOf(new Date(lateInDubai))).toBe("2026-12-04");
    expect(dayKeyOf(new Date(lateInDubai), "Asia/Dubai")).toBe("2026-12-05");
  });

  it("reads a calendar date as the day it names, in any zone", () => {
    setDateTimePreferences({ timeZone: "Pacific/Honolulu", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" });
    expect(formatDate("2026-09-15")).toBe("15 Sep 2026");
    expect(formatWeekdayDate("2026-09-15")).toBe("Tue 15 Sep");
  });

  it("names the offset in a hover title, and lets a caller pin another zone", () => {
    setDateTimePreferences({ timeZone: "Asia/Dubai", dateFormat: "DD_MMM_YYYY", timeFormat: "H24" });
    expect(formatFull(MOMENT)).toBe("Fri, 04 Dec 2026, 14:30 GMT+4");
    expect(formatFull(MOMENT, { timeZone: "Asia/Kolkata" })).toBe("Fri, 04 Dec 2026, 16:00 GMT+5:30");
  });

  it("draws nothing for nothing", () => {
    expect(formatDate(null)).toBe("—");
    expect(formatInstantTime(undefined)).toBeNull();
    expect(formatShortDate("not a date")).toBeNull();
  });
});
