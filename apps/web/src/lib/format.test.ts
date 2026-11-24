import { describe, expect, it } from "vitest";
import { formatTimeAgoShort } from "./format";

describe("formatTimeAgoShort", () => {
  it("says how long ago in a chip's few characters", () => {
    const now = new Date("2026-11-24T12:00:00Z");
    expect(formatTimeAgoShort("2026-11-24T11:59:50Z", now)).toBe("1m ago");
    expect(formatTimeAgoShort("2026-11-24T11:36:00Z", now)).toBe("24m ago");
    expect(formatTimeAgoShort("2026-11-24T09:00:00Z", now)).toBe("3h ago");
    expect(formatTimeAgoShort("2026-11-22T12:00:00Z", now)).toBe("2d ago");
  });
});
