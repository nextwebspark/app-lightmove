import { describe, expect, it } from "vitest";
import type { CompanyProgress, SourcingRun } from "../api/sourcingApi";
import { searchingIdsOf, secondsLeftOf, stepLineOf, timeLeftLine } from "./searchProgress";

const T0 = Date.parse("2026-11-12T10:00:00Z");
const at = (seconds: number) => new Date(T0 + seconds * 1000).toISOString();

const step = (triageCompanyId: string, startedAt: number, finishedAt: number | null = null): CompanyProgress => ({
  triageCompanyId,
  stage: "LOOKING",
  considered: 0,
  done: 0,
  total: 0,
  startedAt: at(startedAt),
  finishedAt: finishedAt === null ? null : at(finishedAt),
});

const runOf = (ids: string[], progress: CompanyProgress[], finished: string[]): SourcingRun => ({
  id: "run1",
  status: "RUNNING",
  companies: ids.map((id) => ({ triageCompanyId: id, companyName: id, searchable: true })),
  companiesTotal: ids.length,
  companiesDone: finished.length,
  suggested: 0,
  profilesBought: 0,
  profilesCached: 0,
  stopRequested: false,
  searched: null,
  outcomes: finished.map((id) => ({
    triageCompanyId: id,
    companyName: id,
    outcome: "FOUND",
    found: 0,
    fits: 0,
    noTitle: 0,
    notFit: 0,
    bought: 0,
    cached: 0,
    matched: null,
    indexFound: 0,
    titleChecks: 0,
  })),
  progress,
  requestedAt: at(0),
  startedAt: at(0),
  finishedAt: null,
  error: null,
});

describe("searchProgress", () => {
  it("counts every company started and not finished as being searched", () => {
    const run = runOf(["a", "b", "c"], [step("a", 0, 20), step("b", 0)], ["a"]);
    expect([...searchingIdsOf(run)]).toEqual(["b"]);
  });

  it("guesses nothing until a company has finished", () => {
    expect(secondsLeftOf(runOf(["a", "b"], [step("a", 0), step("b", 0)], []), 4, T0 + 10_000)).toBeNull();
    expect(timeLeftLine(null)).toBe("Usually a minute or two");
  });

  it("judges what is left from how long a company took: the running ones' remainder, then the waiting in rounds", () => {
    const run = runOf(
      ["a", "b", "c", "d", "e", "f"],
      [step("a", 0, 30), step("b", 0), step("c", 10)],
      ["a"],
    );
    expect(secondsLeftOf(run, 2, T0 + 30_000)).toBe(10 + 2 * 30);
  });

  it("rounds the line so it does not flicker", () => {
    expect(timeLeftLine(8)).toBe("Almost done");
    expect(timeLeftLine(44)).toBe("About 40 s left");
    expect(timeLeftLine(130)).toBe("About 2 min left");
  });

  it("names each step without naming where people are read from", () => {
    expect(stepLineOf(undefined)).toBe("Looking for people in the role…");
    expect(stepLineOf({ ...step("a", 0), stage: "CHECKING", done: 12, total: 40 })).toBe("Checking titles · 12 of 40");
    expect(stepLineOf({ ...step("a", 0), stage: "RANKING" })).toBe("Ranking who fits…");
  });
});
