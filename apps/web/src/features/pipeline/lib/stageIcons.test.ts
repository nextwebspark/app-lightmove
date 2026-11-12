import { describe, expect, it } from "vitest";
import { ICONS } from "../../../components/layout/Icon";
import { stageMarkOf } from "./stageIcons";

describe("a stage's mark on the board", () => {
  it("draws each seeded stage with its own icon and colour, Approach and Offer apart", () => {
    expect(stageMarkOf("identified")).toEqual({ icon: ICONS.stageResearch, colourClass: "text-u-text2" });
    expect(stageMarkOf("interested")).toEqual({ icon: ICONS.stageAssessment, colourClass: "text-u-adjacent" });
    expect(stageMarkOf("qualified")).toEqual({ icon: ICONS.stageShortlist, colourClass: "text-u-chart-3" });
    expect(stageMarkOf("shortlisted")).toEqual({ icon: ICONS.stageOffer, colourClass: "text-u-direct" });
    expect(stageMarkOf("contacted")?.icon).toBe(ICONS.stageApproach);
    expect(stageMarkOf("contacted")?.colourClass).not.toBe(stageMarkOf("shortlisted")?.colourClass);
  });

  it("leaves a stage the workspace added to its colour dot", () => {
    expect(stageMarkOf("references")).toBeNull();
  });
});
