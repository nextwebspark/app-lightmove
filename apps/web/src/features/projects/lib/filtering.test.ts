import { describe, expect, it } from "vitest";
import type { Project } from "../api/types";
import { filterProjects, sortProjects } from "./filtering";

/** The My/All + chips + search + sort combination is the workspace home's core behavior. */

const project = (overrides: Partial<Project>): Project => ({
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy",
  positionTitle: "CFO",
  stage: "MAPPING",
  health: "OK",
  targetDate: "2026-09-15",
  team: [{ memberId: "m1", userId: "u1", fullName: "Alok", workspaceRole: "ADMIN", projectRole: "LEAD" }],
  companies: 0,
  candidates: 0,
  createdAt: "2026-07-01T00:00:00Z",
  ...overrides,
});

describe("filterProjects", () => {
  const mine = project({ id: "mine" });
  const theirs = project({
    id: "theirs",
    team: [{ memberId: "m2", userId: "u2", fullName: "Sara", workspaceRole: "CONSULTANT", projectRole: "LEAD" }],
  });
  const delivered = project({ id: "done", stage: "DELIVERED" });

  it("'my' keeps only projects whose team includes me", () => {
    const rows = filterProjects([mine, theirs], { view: "my", myMemberId: "m1", chip: "allstages", query: "" });
    expect(rows.map((p) => p.id)).toEqual(["mine"]);
  });

  it("the Active chip hides delivered and closed mandates", () => {
    const rows = filterProjects([mine, delivered], { view: "all", chip: "active", query: "" });
    expect(rows.map((p) => p.id)).toEqual(["mine"]);
  });

  it("a stage chip keeps only that stage", () => {
    const rows = filterProjects([mine, delivered], { view: "all", chip: "DELIVERED", query: "" });
    expect(rows.map((p) => p.id)).toEqual(["done"]);
  });

  it("search matches client and position, case-insensitively", () => {
    const rows = filterProjects([mine, project({ id: "other", clientName: "Agthia", positionTitle: "CEO" })], {
      view: "all",
      chip: "allstages",
      query: "meridian",
    });
    expect(rows.map((p) => p.id)).toEqual(["mine"]);

    const byPosition = filterProjects([mine], { view: "all", chip: "allstages", query: "cfo" });
    expect(byPosition).toHaveLength(1);
  });
});

describe("sortProjects", () => {
  const early = project({ id: "early", targetDate: "2026-08-01", stage: "BRIEF", clientName: "Alpha" });
  const late = project({ id: "late", targetDate: "2026-12-01", stage: "OUTREACH", clientName: "Zeta" });
  const undated = project({ id: "undated", targetDate: null });

  it("sorts by target date with undated projects last, and flips with direction", () => {
    expect(sortProjects([late, undated, early], "date", 1).map((p) => p.id)).toEqual([
      "early",
      "late",
      "undated",
    ]);
    expect(sortProjects([early, late], "date", -1).map((p) => p.id)).toEqual(["late", "early"]);
  });

  it("sorts stage by pipeline order, not alphabetically", () => {
    // Alphabetical would put OUTREACH before BRIEF's neighbours; pipeline order must win.
    expect(sortProjects([late, early], "stage", 1).map((p) => p.id)).toEqual(["early", "late"]);
  });

  it("sorts by client name", () => {
    expect(sortProjects([late, early], "client", 1).map((p) => p.id)).toEqual(["early", "late"]);
  });
});
