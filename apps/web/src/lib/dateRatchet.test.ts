import { describe, expect, it } from "vitest";

/**
 * Every date and time reads in the person's own zone and formats (Settings → General, Settings → Profile), which only
 * `lib/datetime` knows. A browser formatter elsewhere reads the computer's zone and its own shapes, so none is allowed
 * outside it; a new shape belongs in `lib/datetime/format.ts`.
 */
const FORBIDDEN = [
  /\.toLocaleDateString\(/,
  /\.toLocaleTimeString\(/,
  /\bIntl\.DateTimeFormat\b/,
  /\.toDateString\(\)/,
  /Date\([^)]*\)\.toLocaleString\(/,
  /toLocaleString\([^)]*(dateStyle|timeStyle|weekday|hour)/,
];

const sources = import.meta.glob<string>(
  ["../**/*.{ts,tsx}", "!../**/*.test.{ts,tsx}", "!../lib/datetime/**", "!../test/**"],
  { query: "?raw", import: "default", eager: true },
);

describe("date ratchet", () => {
  it("formats no date or time outside lib/datetime", () => {
    // An empty glob would pass anything.
    expect(Object.keys(sources).length).toBeGreaterThan(100);
    const offenders = Object.entries(sources).flatMap(([path, source]) =>
      source
        .split("\n")
        .map((line, index) => ({ line, index }))
        .filter(({ line }) => FORBIDDEN.some((pattern) => pattern.test(line)))
        .map(({ index }) => `${path.replace(/^\.\.\//, "")}:${index + 1}`),
    );
    expect(offenders).toEqual([]);
  });
});
