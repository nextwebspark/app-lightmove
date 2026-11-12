import { describe, expect, it } from "vitest";
import { aCurrentCatalog, aSeededCatalog, withRetiredStatus } from "../../../test/fixtures/pipelineCatalog";
import { aPipelineRow } from "../../../test/fixtures/pipelineRows";
import { findStatus } from "../../candidates/lib/pipelineStatus";
import type { PipelineRow } from "../api/types";
import { boardPlacementOf, candidateStageOf, outOfProcessCellsOf, waysOutOf, type BoardTarget } from "./pipelineView";

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
    const { key, label, colour, position } = found.stage;
    return { stage: { key, label, colour, position }, status: keepStatus ? null : found.status, beforeId };
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

describe("the board's Out of process band", () => {
  const columnsOf = (catalog: ReturnType<typeof aCurrentCatalog>) =>
    catalog.stages
      .filter((stage) => stage.statuses.some((status) => !status.outOfProcess))
      .map(candidateStageOf);

  it("puts everyone out of process in the cell under the stage their way out sits in", () => {
    const catalog = aCurrentCatalog();
    const rows = [
      aPipelineRow({ candidateId: "a", status: "withdrew" }, catalog),
      aPipelineRow({ candidateId: "b", status: "notInterested" }, catalog),
      aPipelineRow({ candidateId: "c", status: "contacted" }, catalog),
      aPipelineRow({ candidateId: "d", status: "outOfScope" }, catalog),
      aPipelineRow({ candidateId: "e", status: "unresponsive" }, catalog),
    ];

    expect(
      outOfProcessCellsOf(rows, columnsOf(catalog), catalog).map((cell) => [
        cell.stage.label,
        cell.underColumn,
        cell.rows.map((row) => row.candidateId),
      ]),
    ).toEqual([
      ["Research", true, ["d"]],
      ["Approach", true, ["b", "e"]],
      ["Assessment", true, ["a"]],
      ["Shortlist", true, []],
      ["Offer", true, []],
    ]);
  });

  it("gives a stage of ways out alone, which has no column, a cell of its own after the rest", () => {
    const catalog = aSeededCatalog();
    const rows = [
      aPipelineRow({ candidateId: "a", status: "offLimits" }),
      aPipelineRow({ candidateId: "b", status: "contacted" }),
    ];

    const cells = outOfProcessCellsOf(rows, columnsOf(catalog), catalog);

    expect(cells.map((cell) => [cell.stage.label, cell.underColumn])).toEqual([
      ["Identified", true],
      ["Contacted", true],
      ["Interested", true],
      ["Qualified", true],
      ["Shortlisted", true],
      ["Declined", false],
    ]);
    expect(cells[5].rows.map((row) => row.candidateId)).toEqual(["a"]);
    expect(cells[0].waysOut).toEqual([]);
  });

  it("offers a cell only its own stage's live ways out", () => {
    const keys = waysOutOf(aCurrentCatalog(), "contacted").map((status) => status.key);

    expect(keys).toEqual(["notInterested", "unresponsive"]);
    expect(waysOutOf(aCurrentCatalog(), "qualified").map((status) => status.key)).toEqual(["clientRejected"]);
    expect(waysOutOf(aCurrentCatalog(), "nowhere")).toEqual([]);
  });

  it("offers no retired way out", () => {
    const catalog = withRetiredStatus(aCurrentCatalog(), "unresponsive");

    expect(waysOutOf(catalog, "contacted").map((status) => status.key)).toEqual(["notInterested"]);
  });

  it("marks a card given a way out within its own stage as out of process", () => {
    const catalog = aCurrentCatalog();
    const rows = [aPipelineRow({ candidateId: "a", status: "contacted" }, catalog)];
    const found = findStatus(catalog, "notInterested")!;
    const { key, label, colour, position } = found.stage;

    const placement = boardPlacementOf(rows, "a", {
      stage: { key, label, colour, position },
      status: found.status,
      beforeId: null,
    })!;

    expect(placement.status).toBe("notInterested");
    expect(placement.rows[0]).toMatchObject({ outOfProcess: true, pipelineStage: { key: "contacted" } });
  });
});
