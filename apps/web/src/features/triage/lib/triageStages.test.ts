import { describe, expect, it } from "vitest";
import { OFFERED_TRIAGE_STAGES, offeredStageBySlug, PERSON_FILING_STAGES, stageByStatus } from "./triageStages";
import { isStageOffered, lineMoves, offeredMoves, removeTooltip } from "./triageVocabulary";

describe("Shortlisted switched off, Declined a company's stage", () => {
  it("offers In universe and Declined", () => {
    expect(OFFERED_TRIAGE_STAGES.map((stage) => stage.status)).toEqual(["inUniverse", "declined"]);
    expect([isStageOffered("inUniverse"), isStageOffered("shortlisted"), isStageOffered("declined")]).toEqual([
      true,
      false,
      true,
    ]);
    expect(offeredStageBySlug("universe")?.status).toBe("inUniverse");
    expect(offeredStageBySlug("declined")?.status).toBe("declined");
    expect(offeredStageBySlug("shortlisted")).toBeUndefined();
  });

  it("never files a person at Declined", () => {
    expect(PERSON_FILING_STAGES.map((stage) => stage.status)).toEqual(["inUniverse"]);
  });

  it("still names a stage a row was filed at before", () => {
    expect(stageByStatus("shortlisted").label).toBe("Shortlisted");
  });

  it("offers Decline from In universe in the drawer, and Back to universe from Declined", () => {
    expect(offeredMoves("inUniverse").map((move) => move.status)).toEqual(["declined"]);
    expect(offeredMoves("shortlisted").map((move) => move.status)).toEqual(["inUniverse", "declined"]);
    expect(offeredMoves("declined").map((move) => move.status)).toEqual(["inUniverse"]);
  });

  it("keeps Decline off every grid line", () => {
    expect(lineMoves("inUniverse")).toEqual([]);
    expect(lineMoves("shortlisted").map((move) => move.status)).toEqual(["inUniverse"]);
    expect(lineMoves("declined").map((move) => move.status)).toEqual(["inUniverse"]);
  });

  it("points the trash bin at Decline", () => {
    expect(removeTooltip("ACWA Power")).toBe(
      "Remove ACWA Power from this mandate — not remembered; use Decline to keep it ruled out",
    );
  });
});
