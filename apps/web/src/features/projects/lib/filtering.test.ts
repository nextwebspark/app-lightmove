import { describe, expect, it } from "vitest";
import type { Project } from "../api/types";
import { filterProjects } from "./filtering";

/** The My/All + chips + search + sort combination is the workspace home's core behavior. */

const project = (overrides: Partial<Project>): Project => ({
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy",
  clientLogoUrl: null,
  positionTitle: "CFO",
  stage: "MAPPING",
  health: "OK",
  targetDate: "2026-09-15",
  projectType: "SEARCH",
  startDate: null,
  deliveryDate: null,
  mappingTargetDate: null,
  team: [{ memberId: "m1", userId: "u1", fullName: "Alok", avatarUrl: null, workspaceRoles: ["ADMIN"], projectRoles: ["LEAD"] }],
  representatives: [],
  companies: 0,
  candidates: 0,
  mappedCandidates: 0,
  engagedCandidates: 0,
  mappedCompanies: 0,
  createdAt: "2026-07-01T00:00:00Z",
  archivedAt: null,
  archivedByName: null,
  ...overrides,
});

describe("filterProjects", () => {
  const mine = project({ id: "mine" });
  const theirs = project({
    id: "theirs",
    team: [{ memberId: "m2", userId: "u2", fullName: "Sara", avatarUrl: null, workspaceRoles: ["MEMBER"], projectRoles: ["LEAD"] }],
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

  it("an archived position shows under the Archived chip and under no other", () => {
    const archived = project({ id: "archived", archivedAt: "2026-10-01T09:00:00Z", archivedByName: "Alok" });
    const all = [mine, delivered, archived];

    for (const chip of ["active", "allstages", "MAPPING"] as const) {
      expect(filterProjects(all, { view: "all", chip, query: "" }).map((p) => p.id)).not.toContain("archived");
    }
    expect(filterProjects(all, { view: "all", chip: "archived", query: "" }).map((p) => p.id)).toEqual(["archived"]);
  });
});
