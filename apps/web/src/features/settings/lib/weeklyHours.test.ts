import { describe, expect, it } from "vitest";
import { draftOf, presetOf, weekLabelOf, weekProblemOf, workingDaysOf } from "./weeklyHours";

describe("weeklyHours", () => {
  it("reads the server's week into a draft and back, in week order", () => {
    const draft = draftOf([
      { day: "SUNDAY", start: "09:00:00", end: "18:00:00" },
      { day: "MONDAY", start: "09:00:00", end: "18:00:00" },
    ]);

    expect(draft.FRIDAY).toBeNull();
    expect(workingDaysOf(draft)).toEqual([
      { day: "MONDAY", start: "09:00", end: "18:00" },
      { day: "SUNDAY", start: "09:00", end: "18:00" },
    ]);
  });

  it("names a week by its runs of days and hours", () => {
    expect(weekLabelOf([{ day: "SUNDAY", start: "09:00", end: "18:00" }, { day: "MONDAY", start: "09:00", end: "18:00" }])).toBe(
      "Sun–Mon, 09:00–18:00",
    );
    expect(
      weekLabelOf([
        { day: "MONDAY", start: "09:00", end: "18:00" },
        { day: "TUESDAY", start: "09:00", end: "18:00" },
        { day: "THURSDAY", start: "09:00", end: "13:00" },
      ]),
    ).toBe("Mon–Tue 09:00–18:00 · Thu 09:00–13:00");
  });

  it("presets a week from the hours already chosen, and refuses one nobody works", () => {
    const draft = draftOf([{ day: "MONDAY", start: "10:00", end: "16:00" }]);

    expect(workingDaysOf(presetOf(draft, ["SUNDAY", "THURSDAY"]))).toEqual([
      { day: "THURSDAY", start: "10:00", end: "16:00" },
      { day: "SUNDAY", start: "10:00", end: "16:00" },
    ]);
    expect(weekProblemOf(presetOf(draft, []))).toBe("Choose at least one working day.");
    expect(weekProblemOf({ ...draft, MONDAY: { start: "16:00", end: "10:00" } })).toBe(
      "A working day has to end after it starts.",
    );
  });
});
