import { describe, expect, it } from "vitest";
import type { Project } from "../api/types";
import { projectProgress } from "./projectProgress";

const base: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Automotive",
  clientLogoUrl: null,
  positionTitle: "CFO",
  stage: "MAPPING",
  health: "OK",
  targetDate: "2026-10-04",
  projectType: "SEARCH",
  startDate: null,
  deliveryDate: null,
  mappingTargetDate: null,
  team: [],
  representatives: [],
  companies: 19,
  candidates: 14,
  engagedCandidates: 0,
  mappedCompanies: 12,
  createdAt: "2026-08-27T09:00:00Z",
};

const today = new Date(2026, 8, 24, 15, 30);

describe("projectProgress", () => {
  it("reads coverage off the universe, not the calendar", () => {
    expect(projectProgress(base, today).coveragePercent).toBe(63);
  });

  it("reports no coverage for an empty universe rather than dividing by it", () => {
    expect(projectProgress({ ...base, companies: 0, mappedCompanies: 0 }, today).coveragePercent).toBe(0);
  });

  it("counts whole days to the target, negative once passed, null without one", () => {
    expect(projectProgress(base, today).daysRemaining).toBe(10);
    expect(projectProgress({ ...base, targetDate: "2026-09-21" }, today).daysRemaining).toBe(-3);
    expect(projectProgress({ ...base, targetDate: null }, today).daysRemaining).toBeNull();
  });

  it("states velocity per week open, never over less than a week", () => {
    expect(projectProgress(base, today).weeklyVelocity).toBe("3.5");
    expect(projectProgress({ ...base, createdAt: "2026-09-23T09:00:00Z" }, today).weeklyVelocity).toBe("14.0");
  });
});
