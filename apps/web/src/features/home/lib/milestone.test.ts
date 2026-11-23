import { describe, expect, it } from "vitest";
import type { Project } from "../../projects/api/types";
import { nextMilestoneOf } from "./milestone";

const position = (overrides: Partial<Project>): Project => ({
  id: "p1",
  clientId: "c1",
  clientName: "Al-Futtaim",
  clientLogoUrl: null,
  positionTitle: "Leasing Director",
  stage: "MAPPING",
  health: "OK",
  targetDate: null,
  projectType: "SEARCH",
  startDate: null,
  deliveryDate: null,
  mappingTargetDate: null,
  team: [],
  representatives: [],
  companies: 0,
  candidates: 0,
  mappedCandidates: 0,
  engagedCandidates: 0,
  mappedCompanies: 0,
  createdAt: "2026-07-13T10:00:00Z",
  archivedAt: null,
  archivedByName: null,
  ...overrides,
});

describe("nextMilestoneOf", () => {
  const today = "2026-11-23";

  it("names the universe's mapping date first while outreach has not started", () => {
    const milestone = nextMilestoneOf(position({ mappingTargetDate: "2026-11-24", deliveryDate: "2026-12-07" }), today);
    expect(milestone).toEqual({ label: "Universe mapped", when: "Tomorrow", overdue: false });
  });

  it("moves on to the shortlist once outreach is live", () => {
    const milestone = nextMilestoneOf(
      position({ stage: "OUTREACH", mappingTargetDate: "2026-11-24", deliveryDate: "2026-11-23" }),
      today,
    );
    expect(milestone).toEqual({ label: "Shortlist delivered", when: "Today", overdue: false });
  });

  it("says a passed delivery date is overdue, and calls a mapping project's delivery the map", () => {
    const milestone = nextMilestoneOf(position({ projectType: "MAPPING", deliveryDate: "2026-11-21" }), today);
    expect(milestone?.label).toBe("Map delivered overdue");
    expect(milestone?.overdue).toBe(true);
    expect(milestone?.when).toMatch(/21/);
  });

  it("falls back to the target start while it is ahead, and to nothing once it has passed", () => {
    expect(nextMilestoneOf(position({ stage: "DELIVERED", deliveryDate: "2026-11-01", targetDate: "2027-01-08" }), today)?.label)
      .toBe("Target start");
    expect(nextMilestoneOf(position({ targetDate: "2026-11-01" }), today)).toBeNull();
  });
});
