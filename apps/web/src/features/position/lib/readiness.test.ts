import { describe, expect, it } from "vitest";
import type { Position } from "../api/types";
import { completion, readiness } from "./readiness";

const criteria = (mode: "REQUIRED" | "PREFERRED") => [{ text: "x", mode, fromBrief: false }];
const rows = (...weights: number[]) => weights.map((weight, i) => ({ name: `C${i}`, weight }));

describe("readiness", () => {
  it("is ready when both panels total 100 and a required criterion exists", () => {
    const r = readiness({
      technical: rows(60, 40),
      behavioural: rows(100),
      criteria: criteria("REQUIRED"),
    });
    expect(r).toEqual({ technicalTotal: 100, behaviouralTotal: 100, hasRequired: true, ready: true });
  });

  it("an unbalanced panel blocks the lock and reports its live total", () => {
    const r = readiness({
      technical: rows(60, 30),
      behavioural: rows(100),
      criteria: criteria("REQUIRED"),
    });
    expect(r.technicalTotal).toBe(90);
    expect(r.ready).toBe(false);
  });

  it("preferred-only criteria are not enough", () => {
    const r = readiness({
      technical: rows(100),
      behavioural: rows(100),
      criteria: criteria("PREFERRED"),
    });
    expect(r.hasRequired).toBe(false);
    expect(r.ready).toBe(false);
  });
});

describe("completion", () => {
  const seeded: Position = {
    mandateReason: "NEW_ROLE",
    internalContext: null,
    narrative: "A narrative.",
    reportsTo: "Group CEO",
    directReports: null,
    teamSize: null,
    location: "UAE",
    employmentType: "FULL_TIME_PERMANENT",
    startTarget: null,
    salaryMin: null,
    salaryMax: null,
    currency: "USD",
    noticeValue: null,
    noticeUnit: null,
    bonusTargetPct: null,
    ltip: null,
    benefits: [],
    confidential: false,
    criteria: criteria("REQUIRED"),
    technical: rows(100),
    behavioural: rows(100),
    locked: false,
    lockedAt: null,
    briefDocument: null,
  };

  it("scores a freshly seeded brief part-done, not zero and not complete", () => {
    const score = completion(seeded);
    expect(score).toBeGreaterThan(0);
    expect(score).toBeLessThan(100);
  });

  it("filling everything reaches 100", () => {
    const full: Position = {
      ...seeded,
      internalContext: "ctx",
      directReports: 4,
      teamSize: 38,
      startTarget: "2026-09-15",
      salaryMin: 450000,
      salaryMax: 550000,
      noticeValue: 3,
      noticeUnit: "MONTHS",
      bonusTargetPct: 40,
      ltip: "LTIP",
      benefits: ["Housing"],
    };
    expect(completion(full)).toBe(100);
  });
});
