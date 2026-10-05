import { describe, expect, it } from "vitest";
import type { SequenceSchedule, SequenceStep } from "../api/sequenceApi";
import {
  daysLabelOf,
  DEFAULT_SCHEDULE,
  followUpDatesOf,
  instantOf,
  isOutsideSchedule,
  nextOpeningOf,
  SUNDAY_TO_THURSDAY,
  wallClockOf,
} from "./sendSchedule";

const DUBAI = "Asia/Dubai";
const GULF_WEEK: SequenceSchedule = { days: SUNDAY_TO_THURSDAY, windowStart: "09:00:00", windowEnd: "17:00:00" };

describe("sendSchedule", () => {
  it("reads and writes a wall clock in the sender's zone, across a daylight-saving change", () => {
    expect(instantOf("2026-10-05", "08:00", DUBAI).toISOString()).toBe("2026-10-05T04:00:00.000Z");
    expect(instantOf("2026-07-06", "08:00", "Europe/London").toISOString()).toBe("2026-07-06T07:00:00.000Z");
    expect(instantOf("2026-12-07", "08:00", "Europe/London").toISOString()).toBe("2026-12-07T08:00:00.000Z");
    expect(wallClockOf(new Date("2026-10-04T05:30:00Z"), DUBAI)).toEqual({
      date: "2026-10-04",
      time: "09:30",
      weekday: "SUNDAY",
    });
  });

  it("names a run of days by its ends, wrapping the week, and lists the rest", () => {
    expect(daysLabelOf(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"])).toBe("Mon–Fri");
    expect(daysLabelOf(SUNDAY_TO_THURSDAY)).toBe("Sun–Thu");
    expect(daysLabelOf(["MONDAY", "WEDNESDAY"])).toBe("Mon, Wed");
  });

  it("opens now inside the window, else on the next sending day's start", () => {
    const thursdayEvening = instantOf("2026-10-01", "17:30", DUBAI);
    expect(nextOpeningOf(GULF_WEEK, thursdayEvening, DUBAI).toISOString()).toBe(
      instantOf("2026-10-04", "09:00", DUBAI).toISOString(),
    );
    const mondayMorning = instantOf("2026-10-05", "10:00", DUBAI);
    expect(nextOpeningOf(DEFAULT_SCHEDULE, mondayMorning, DUBAI)).toBe(mondayMorning);
  });

  it("dates each follow-up on the schedule's days, at its own time or the time before it", () => {
    const steps: SequenceStep[] = [
      { delayWorkingDays: 0, subject: "Hi", body: "Hi" },
      { delayWorkingDays: 1, subject: null, body: "Again", sendTime: "09:30:00" },
      { delayWorkingDays: 2, subject: null, body: "Last" },
    ];
    const dates = followUpDatesOf(GULF_WEEK, steps, instantOf("2026-10-01", "14:00", DUBAI), DUBAI);
    expect(dates.map((date) => date.toISOString())).toEqual([
      instantOf("2026-10-04", "09:30", DUBAI).toISOString(),
      instantOf("2026-10-06", "09:30", DUBAI).toISOString(),
    ]);
  });

  it("knows a chosen time outside the days or the hours", () => {
    expect(isOutsideSchedule(GULF_WEEK, instantOf("2026-10-02", "10:00", DUBAI), DUBAI)).toBe(true);
    expect(isOutsideSchedule(GULF_WEEK, instantOf("2026-10-04", "17:00", DUBAI), DUBAI)).toBe(true);
    expect(isOutsideSchedule(GULF_WEEK, instantOf("2026-10-04", "16:30", DUBAI), DUBAI)).toBe(false);
  });
});
