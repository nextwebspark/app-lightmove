import { describe, expect, it } from "vitest";
import { OFFERED_TRIAGE_STAGES, offeredStageBySlug, stageByStatus } from "./triageStages";
import { isStageOffered, offeredMoves, removeTooltip } from "./triageVocabulary";

describe("Shortlisted and Declined, switched off", () => {
  it("offers In universe alone", () => {
    expect(OFFERED_TRIAGE_STAGES.map((stage) => stage.status)).toEqual(["inUniverse"]);
    expect([isStageOffered("inUniverse"), isStageOffered("shortlisted"), isStageOffered("declined")]).toEqual([
      true,
      false,
      false,
    ]);
    expect(offeredStageBySlug("universe")?.status).toBe("inUniverse");
    expect(offeredStageBySlug("shortlisted")).toBeUndefined();
    expect(offeredStageBySlug("declined")).toBeUndefined();
  });

  it("still names a stage a row was filed at before", () => {
    expect(stageByStatus("shortlisted").label).toBe("Shortlisted");
    expect(stageByStatus("declined").label).toBe("Declined");
  });

  it("offers no move from In universe, and only the way back from a stage already held", () => {
    expect(offeredMoves("inUniverse")).toEqual([]);
    expect(offeredMoves("shortlisted").map((move) => move.status)).toEqual(["inUniverse"]);
    expect(offeredMoves("declined").map((move) => move.status)).toEqual(["inUniverse"]);
  });

  it("does not point the trash bin at Decline", () => {
    expect(removeTooltip("ACWA Power")).toBe("Remove ACWA Power from this mandate");
  });
});
