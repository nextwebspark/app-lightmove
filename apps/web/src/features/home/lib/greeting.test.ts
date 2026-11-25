import { describe, expect, it } from "vitest";
import { greetingOf } from "./greeting";

describe("greetingOf", () => {
  it("greets by the hour in the person's zone, not the browser's", () => {
    const instant = new Date("2026-11-24T20:30:00Z");
    expect(greetingOf("Yara", instant, "Asia/Tokyo")).toBe("Good morning, Yara.");
    expect(greetingOf("Yara", instant, "Europe/London")).toBe("Good evening, Yara.");
    expect(greetingOf(undefined, new Date("2026-11-24T13:00:00Z"), "UTC")).toBe("Good afternoon.");
  });
});
