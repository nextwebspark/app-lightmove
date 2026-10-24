import { describe, expect, it } from "vitest";
import {
  choiceOfDrag,
  choiceOfFields,
  fieldsOf,
  isWithinWorkingHours,
  refusalOf,
  stretchesOn,
  viewDaysOf,
} from "./weekGrid";

const DUBAI = "Asia/Dubai";
const LIMITS = { minMinutes: 5, maxMinutes: 480, clickMinutes: 30 };
const RULES = { now: new Date("2099-10-05T00:00:00Z"), minMinutes: 5, maxMinutes: 480, latestDate: "2100-04-05", timeZone: DUBAI, busy: [] };

describe("weekGrid", () => {
  it("lists the view's days from its first", () => {
    expect(viewDaysOf("2099-10-30", 3)).toEqual(["2099-10-30", "2099-10-31", "2099-11-01"]);
  });

  it("places a stretch by its minutes from the day's own midnight, clipped to the day", () => {
    const spans = [
      { start: "2099-10-05T04:00:00Z", end: "2099-10-05T14:00:00Z" },
      { start: "2099-10-05T18:00:00Z", end: "2099-10-06T22:00:00Z" },
    ];

    expect(stretchesOn(spans, "2099-10-05", DUBAI)).toEqual([
      { top: 480, bottom: 1080 },
      { top: 1320, bottom: 1440 },
    ]);
    expect(stretchesOn(spans, "2099-10-06", DUBAI)).toEqual([{ top: 0, bottom: 1440 }]);
  });

  it("reads a drag as the call it covers, on five-minute marks, and a click as the chosen length on the quarter hour", () => {
    expect(choiceOfDrag("2099-10-05", 613, 667, DUBAI, LIMITS)).toEqual({
      start: "2099-10-05T06:15:00.000Z",
      minutes: 50,
    });
    expect(choiceOfDrag("2099-10-05", 667, 613, DUBAI, LIMITS)).toEqual({
      start: "2099-10-05T06:15:00.000Z",
      minutes: 50,
    });
    expect(choiceOfDrag("2099-10-05", 608, 610, DUBAI, LIMITS)).toEqual({
      start: "2099-10-05T06:00:00.000Z",
      minutes: 30,
    });
    expect(choiceOfDrag("2099-10-05", 0, 1440, DUBAI, LIMITS).minutes).toBe(480);
  });

  it("turns the Pick a time fields into a call and back, a call to midnight included", () => {
    const call = choiceOfFields("2099-10-05", "19:40", "20:30", DUBAI);
    expect(call).toEqual({ start: "2099-10-05T15:40:00.000Z", minutes: 50 });
    expect(fieldsOf(call!, DUBAI)).toEqual({ date: "2099-10-05", from: "19:40", to: "20:30" });

    const late = choiceOfFields("2099-10-05", "23:00", "00:00", DUBAI);
    expect(late?.minutes).toBe(60);
    expect(fieldsOf(late!, DUBAI).to).toBe("24:00");
    expect(choiceOfFields("2099-10-05", "11:00", "10:00", DUBAI)).toBeNull();
  });

  it("says a call outside working hours is so, and a call inside one stretch is not", () => {
    const hours = [{ start: "2099-10-05T04:00:00Z", end: "2099-10-05T14:00:00Z" }];

    expect(isWithinWorkingHours({ start: "2099-10-05T06:00:00Z", minutes: 30 }, hours)).toBe(true);
    expect(isWithinWorkingHours({ start: "2099-10-05T13:45:00Z", minutes: 30 }, hours)).toBe(false);
  });

  it("refuses what the server would: off the mark, too long, past, too far ahead, or over a busy time", () => {
    expect(refusalOf({ start: "2099-10-05T06:03:00Z", minutes: 30 }, RULES)).toBe("Pick times on a five-minute mark.");
    expect(refusalOf({ start: "2099-10-05T06:00:00Z", minutes: 485 }, RULES)).toBe("A call runs 5 min to 8 hr.");
    expect(refusalOf({ start: "2099-10-04T06:00:00Z", minutes: 30 }, RULES)).toBe("That time has passed. Pick a later one.");
    expect(refusalOf({ start: "2100-05-01T06:00:00Z", minutes: 30 }, RULES)).toBe(
      "That's further ahead than calls can be booked.",
    );
    expect(
      refusalOf(
        { start: "2099-10-05T06:00:00Z", minutes: 30 },
        { ...RULES, busy: [{ start: "2099-10-05T06:15:00Z", end: "2099-10-05T07:00:00Z" }] },
      ),
    ).toBe("Your calendar is busy then.");
    expect(refusalOf({ start: "2099-10-05T06:00:00Z", minutes: 30 }, RULES)).toBeNull();
  });

  it("reads a row as the wall-clock time on a day the clocks go back, in a drag and in the spans drawn", () => {
    // 25 October 2026: London's clocks go back at 02:00, so 14:00 is 13:00 UTC, not the 14:00 UTC of midnight + 14h.
    expect(choiceOfDrag("2026-10-25", 14 * 60, 15 * 60, "Europe/London", LIMITS)).toEqual({
      start: "2026-10-25T14:00:00.000Z",
      minutes: 60,
    });
    expect(
      stretchesOn([{ start: "2026-10-25T14:00:00Z", end: "2026-10-25T15:00:00Z" }], "2026-10-25", "Europe/London"),
    ).toEqual([{ top: 14 * 60, bottom: 15 * 60 }]);
    // 29 March 2026: clocks go forward at 01:00, so 14:00 is 13:00 UTC.
    expect(choiceOfDrag("2026-03-29", 14 * 60, 14 * 60 + 30, "Europe/London", LIMITS).start).toBe(
      "2026-03-29T13:00:00.000Z",
    );
  });
});
