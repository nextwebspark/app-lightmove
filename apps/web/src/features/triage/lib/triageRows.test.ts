import { describe, expect, it } from "vitest";
import type { Candidate } from "../../candidates/api/types";
import { awaitingResearch } from "./triageRows";

/** What keeps the In-universe screen polling, and what lets it stop. */
describe("awaitingResearch", () => {
  const captured = (overrides: Partial<Candidate>): Candidate =>
    ({
      id: "c1",
      source: "extension",
      enrichedAt: null,
      addedAt: new Date(Date.now() - 30_000).toISOString(),
      ...overrides,
    }) as Candidate;

  it("holds while a fresh plugin capture is still unenriched", () => {
    expect(awaitingResearch([captured({})])).toBe(true);
  });

  it("stops once the research lands", () => {
    expect(awaitingResearch([captured({ enrichedAt: "2026-09-02T10:00:00Z" })])).toBe(false);
  });

  it("gives up on a capture whose research never came", () => {
    const stale = captured({ addedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString() });
    expect(awaitingResearch([stale])).toBe(false);
  });

  it("never polls for a manually typed executive", () => {
    expect(awaitingResearch([captured({ source: "manual" })])).toBe(false);
  });
});
