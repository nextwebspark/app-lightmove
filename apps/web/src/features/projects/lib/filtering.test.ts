import { describe, expect, it } from "vitest";
import { sampleProject } from "../../../test/sampleProject";
import type { TeamMember } from "../api/types";
import { filterProjects } from "./filtering";

/** The My/All + chips + search combination is the workspace home's core behavior. */

const SEAT: TeamMember = {
  memberId: "m1",
  userId: "u1",
  fullName: "Alok",
  avatarUrl: null,
  workspaceRoles: ["ADMIN"],
  projectRoles: ["LEAD"],
};

describe("filterProjects", () => {
  const mine = sampleProject({ id: "mine", team: [SEAT] });
  const theirs = sampleProject({
    id: "theirs",
    team: [{ ...SEAT, memberId: "m2", userId: "u2", fullName: "Sara", workspaceRoles: ["MEMBER"] }],
  });
  const delivered = sampleProject({ id: "done", team: [SEAT], stage: "DELIVERED" });

  it("'my' keeps only projects whose team includes me", () => {
    const rows = filterProjects([mine, theirs], { view: "my", myMemberId: "m1", chip: "all", query: "" });
    expect(rows.map((project) => project.id)).toEqual(["mine"]);
  });

  it("the Active chip hides delivered and closed mandates", () => {
    const rows = filterProjects([mine, delivered], { view: "all", chip: "active", query: "" });
    expect(rows.map((project) => project.id)).toEqual(["mine"]);
  });

  it("a type chip keeps only mandates engaged to deliver that", () => {
    const search = sampleProject({ id: "search", projectType: "EXECUTIVE_SEARCH" });
    expect(
      filterProjects([mine, search], { view: "all", chip: "EXECUTIVE_SEARCH", query: "" })
        .map((project) => project.id),
    ).toEqual(["search"]);
    expect(
      filterProjects([mine, search], { view: "all", chip: "MAPPING", query: "" })
        .map((project) => project.id),
    ).toEqual(["mine"]);
  });

  it("Needs attention keeps the mandates that are behind, whatever their type", () => {
    const slipping = sampleProject({ id: "risk", health: "RISK" });
    const late = sampleProject({ id: "off", health: "OFF" });
    const rows = filterProjects([mine, slipping, late], { view: "all", chip: "attention", query: "" });
    expect(rows.map((project) => project.id)).toEqual(["risk", "off"]);
  });

  it("search matches client and position, case-insensitively", () => {
    const other = sampleProject({ id: "other", clientName: "Agthia", positionTitle: "CEO" });
    const rows = filterProjects([mine, other], { view: "all", chip: "all", query: "meridian" });
    expect(rows.map((project) => project.id)).toEqual(["mine"]);

    expect(filterProjects([mine], { view: "all", chip: "all", query: "cfo" })).toHaveLength(1);
  });
});
