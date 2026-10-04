import { describe, expect, it } from "vitest";
import { shiftDateOf, slotDayLabelOf, slotDayPartsOf, slotTimeOf } from "./meetingTimes";

describe("meetingTimes", () => {
  it("labels a slot day as the consultant's own date, whatever the viewer's zone", () => {
    expect(slotDayLabelOf("2026-10-02")).toBe("Fri 2 Oct");
    expect(slotDayLabelOf("2026-10-05")).toBe("Mon 5 Oct");
  });

  it("reads a slot's time in the consultant's zone", () => {
    // 06:00 UTC is 10:00 in Dubai.
    expect(slotTimeOf("2026-10-02T06:00:00Z", "Asia/Dubai")).toBe("10:00");
    expect(slotTimeOf("2026-10-02T06:30:00Z", "Europe/London")).toBe("07:30");
  });

  it("splits a slot day into its tile's lines", () => {
    expect(slotDayPartsOf("2026-10-05")).toEqual({ weekday: "MON", day: "5", month: "Oct" });
  });

  it("moves a day across month and year ends", () => {
    expect(shiftDateOf("2026-10-30", 7)).toBe("2026-11-06");
    expect(shiftDateOf("2027-01-03", -7)).toBe("2026-12-27");
  });
});
