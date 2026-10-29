import { describe, expect, it } from "vitest";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { aPipelineRow } from "../../../test/fixtures/pipelineRows";
import { findStatus } from "../../candidates/lib/pipelineStatus";
import type { PipelineRow } from "../api/types";
import {
  boardPlacementOf,
  daysInStage,
  inactiveGroupsOf,
  type BoardTarget,
} from "./pipelineView";

const NOW = new Date(2026, 9, 4, 12, 0);

describe("days in stage", () => {
  it("counts whole days since the status changed, never below zero", () => {
    expect(daysInStage(new Date(2026, 8, 30, 12, 0).toISOString(), NOW)).toBe(4);
    expect(daysInStage(new Date(2026, 9, 5).toISOString(), NOW)).toBe(0);
  });
});

describe("a drop on the board", () => {
  const rows = [
    aPipelineRow({ candidateId: "a", status: "identified" }),
    aPipelineRow({ candidateId: "b", status: "researching" }),
    aPipelineRow({ candidateId: "c", status: "identified" }),
    aPipelineRow({ candidateId: "d", status: "screened" }),
    aPipelineRow({ candidateId: "e", status: "qualified" }),
    aPipelineRow({ candidateId: "f", status: "offLimits" }),
  ];
  const order = (placed: PipelineRow[]) => placed.map((row) => row.candidateId);
  const targetIn = (statusKey: string, beforeId: string | null, keepStatus = false): BoardTarget => {
    const found = findStatus(aSeededCatalog(), statusKey)!;
    const { key, label, colour, kind, position } = found.stage;
    return { stage: { key, label, colour, kind, position }, status: keepStatus ? null : found.status, beforeId };
  };

  it("reorders within a stage, sending only the new neighbours", () => {
    const placement = boardPlacementOf(rows, "c", targetIn("identified", "a", true))!;

    expect(order(placement.rows)).toEqual(["c", "a", "b", "d", "e", "f"]);
    expect(placement.move).toEqual({ afterCandidateId: null, beforeCandidateId: "a" });
    expect(placement.status).toBeNull();
  });

  it("lands nowhere when the card is dropped where it already is", () => {
    expect(boardPlacementOf(rows, "b", targetIn("identified", "b", true))).toBeNull();
    expect(boardPlacementOf(rows, "b", targetIn("identified", "c", true))).toBeNull();
    expect(boardPlacementOf(rows, "c", targetIn("identified", null, true))).toBeNull();
  });

  it("gives a card dropped on another stage its status and puts it where it was dropped", () => {
    const now = new Date("2026-10-14T10:00:00Z");
    const placement = boardPlacementOf(rows, "a", targetIn("screened", "e"), now)!;

    expect(order(placement.rows)).toEqual(["b", "c", "d", "a", "e", "f"]);
    expect(placement.move).toEqual({ afterCandidateId: "d", beforeCandidateId: "e" });
    expect(placement.status).toBe("screened");
    expect(placement).toMatchObject({ fromStageKey: "identified", stageKey: "qualified", stageLabel: "Qualified" });
    expect(placement.rows[3]).toMatchObject({
      status: "screened",
      statusLabel: "Screened",
      pipelineStage: { key: "qualified" },
      statusChangedAt: now.toISOString(),
    });
  });

  it("puts a card dropped past a stage's last card at its end, and on an empty stage where that stage begins", () => {
    expect(order(boardPlacementOf(rows, "a", targetIn("screened", null))!.rows)).toEqual(["b", "c", "d", "e", "a", "f"]);

    const empty = boardPlacementOf(rows, "a", targetIn("interested", null))!;
    expect(order(empty.rows)).toEqual(["b", "c", "a", "d", "e", "f"]);
    expect(empty.move).toBeNull();
  });

  it("files a card dropped on an Inactive status last in the closed stage, where the rank write keeps it", () => {
    const placement = boardPlacementOf(rows, "a", targetIn("notInterested", null))!;

    expect(order(placement.rows)).toEqual(["b", "c", "d", "e", "f", "a"]);
    expect(placement.status).toBe("notInterested");
    expect(placement.move).toEqual({ afterCandidateId: "f", beforeCandidateId: null });
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
