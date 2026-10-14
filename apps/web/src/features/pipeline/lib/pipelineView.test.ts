import { describe, expect, it } from "vitest";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { aPipelineRow } from "../../../test/fixtures/pipelineRows";
import { daysInStage, inactiveGroupsOf, rankMoveOf } from "./pipelineView";

const NOW = new Date(2026, 9, 4, 12, 0);

describe("days in stage", () => {
  it("counts whole days since the status changed, never below zero", () => {
    expect(daysInStage(new Date(2026, 8, 30, 12, 0).toISOString(), NOW)).toBe(4);
    expect(daysInStage(new Date(2026, 9, 5).toISOString(), NOW)).toBe(0);
  });
});

describe("a drag in the list", () => {
  const rows = [
    aPipelineRow({ candidateId: "a", status: "identified" }),
    aPipelineRow({ candidateId: "b", status: "identified" }),
    aPipelineRow({ candidateId: "c", status: "identified" }),
    aPipelineRow({ candidateId: "d", status: "contacted" }),
  ];

  it("lands the row between its new neighbours within its stage", () => {
    expect(rankMoveOf(rows, "c", "a")?.move).toEqual({ afterCandidateId: null, beforeCandidateId: "a" });
    expect(rankMoveOf(rows, "a", "b")?.move).toEqual({ afterCandidateId: "b", beforeCandidateId: "c" });
    expect(rankMoveOf(rows, "a", "c")?.move).toEqual({ afterCandidateId: "c", beforeCandidateId: null });
    expect(rankMoveOf(rows, "c", "a")?.rows.map((row) => row.candidateId)).toEqual(["c", "a", "b", "d"]);
  });

  it("never moves a row into another stage", () => {
    expect(rankMoveOf(rows, "a", "d")).toBeNull();
  });
});

describe("the board's Inactive panel", () => {
  it("groups the closed stage by status, in the catalog's order, only where someone holds it", () => {
    const rows = [
      aPipelineRow({ candidateId: "a", status: "offLimits" }),
      aPipelineRow({ candidateId: "b", status: "notInterested" }),
      aPipelineRow({ candidateId: "c", status: "contacted" }),
      aPipelineRow({ candidateId: "d", status: "offLimits" }),
    ];

    expect(
      inactiveGroupsOf(rows, aSeededCatalog()).map((group) => [group.label, group.rows.map((row) => row.candidateId)]),
    ).toEqual([
      ["Not interested", ["b"]],
      ["Off-limits", ["a", "d"]],
    ]);
  });
});
