import { describe, expect, it } from "vitest";
import { isOfferedTimeZone, OFFERED_TIME_ZONES, timeZoneOptionLabel, timeZonesByOffset } from "./timeZones";

describe("the fixed time zone list", () => {
  it("is the API's list exactly, so nothing offered here is refused there", async () => {
    // Read at test time, never imported: the container image builds apps/web alone, and `tsc -b` there would find no
    // file at an import's path. Loaded as styleRatchet does — the app's tsconfig carries no Node types — and relative
    // to apps/web, where vitest runs.
    const fs = (await import(/* @vite-ignore */ `node:${"fs"}`)) as {
      readFileSync: (path: string, encoding: "utf8") => string;
    };
    const apiTimeZones = JSON.parse(fs.readFileSync("../api/src/main/resources/data/time-zones.json", "utf8"));
    expect(OFFERED_TIME_ZONES).toEqual(apiTimeZones);
  });

  it("labels each zone by today's offset and its cities, west to east", () => {
    const winter = new Date("2026-01-15T12:00:00Z");
    const dubai = OFFERED_TIME_ZONES.find((offered) => offered.zone === "Asia/Dubai")!;
    const kolkata = OFFERED_TIME_ZONES.find((offered) => offered.zone === "Asia/Kolkata")!;

    expect(timeZoneOptionLabel(dubai, winter)).toBe("(GMT+04:00) Dubai, Abu Dhabi");
    expect(timeZoneOptionLabel(kolkata, winter)).toBe("(GMT+05:30) Mumbai, New Delhi");
    const ordered = timeZonesByOffset(winter).map((offered) => offered.zone);
    expect(ordered[0]).toBe("Pacific/Honolulu");
    expect(ordered.at(-1)).toBe("Pacific/Auckland");
  });

  it("offers only what it lists", () => {
    expect(isOfferedTimeZone("Asia/Qatar")).toBe(true);
    expect(isOfferedTimeZone("Asia/Tashkent")).toBe(false);
    expect(isOfferedTimeZone(null)).toBe(false);
  });
});
