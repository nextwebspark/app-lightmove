import { describe, expect, it } from "vitest";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { aPipelineRow } from "../../../test/fixtures/pipelineRows";
import { findStatus } from "../../candidates/lib/pipelineStatus";
import type { PipelineRow } from "../api/types";
import {
  boardPlacementOf,
  daysInStage,
  dueLabelOf,
  dueToneOf,
  inactiveGroupsOf,
  rankMoveOf,
  type BoardTarget,
} from "./pipelineView";

const NOW = new Date(2026, 9, 4, 12, 0);

describe("next action due", () => {
  it("is overdue once its day has passed, due today all day, and plain after", () => {
    expect(dueToneOf(new Date(2026, 9, 3, 16, 0).toISOString(), NOW)).toBe("overdue");
    expect(dueToneOf(new Date(2026, 9, 4, 9, 0).toISOString(), NOW)).toBe("today");
    expect(dueToneOf(new Date(2026, 9, 4, 16, 0).toISOString(), NOW)).toBe("today");
    expect(dueToneOf(new Date(2026, 9, 8, 9, 0).toISOString(), NOW)).toBe("later");
  });

  it("reads as the mockup words it", () => {
    expect(dueLabelOf(new Date(2026, 9, 3, 16, 0).toISOString(), NOW)).toBe("Overdue · 03 Oct");
    expect(dueLabelOf(new Date(2026, 9, 4, 16, 0).toISOString(), NOW)).toBe("Today · 16:00");
    expect(dueLabelOf(new Date(2026, 9, 8, 9, 0).toISOString(), NOW)).toBe("08 Oct");
  });
});

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
    return { stage: { key, label, colour, kind, position }, status: keepStatus ? null : found.status, beforeId, ranked: true };
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

  it("sets an Inactive status without a rank write", () => {
    const placement = boardPlacementOf(rows, "a", { ...targetIn("notInterested", null), ranked: false })!;

    expect(placement.status).toBe("notInterested");
    expect(placement.move).toBeNull();
    expect(boardPlacementOf(rows, "f", { ...targetIn("offLimits", null), ranked: false })).toBeNull();
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
