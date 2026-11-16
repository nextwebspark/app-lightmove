import { describe, expect, it } from "vitest";
import type { LastCall } from "../api/types";
import { lastCallLine } from "./lastCall";

const NOW = new Date("2026-11-16T10:00:00Z");

function call(overrides: Partial<LastCall>): LastCall {
  return { at: "2026-09-14T08:30:00Z", outcome: "no_answer", byUserId: "u2", byName: "Sara Al-Mansour", ...overrides };
}

describe("last call line", () => {
  it("names the day, how it went and who called", () => {
    expect(lastCallLine(call({}), NOW)).toBe("Last call 14 Sep · No answer · Sara Al-Mansour");
  });

  it("names the year of a call from an earlier one", () => {
    expect(lastCallLine(call({ at: "2025-12-02T08:30:00Z" }), NOW)).toBe(
      "Last call 02 Dec 2025 · No answer · Sara Al-Mansour",
    );
  });

  it("leaves out an outcome the call never named, and keeps a former member's call", () => {
    expect(lastCallLine(call({ outcome: null, byName: null }), NOW)).toBe("Last call 14 Sep · A former member");
  });
});
