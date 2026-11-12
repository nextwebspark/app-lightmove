import { describe, expect, it } from "vitest";
import type { PipelineCatalog, PipelineStatus } from "../api/types";
import { pickableGroups, startingStatusKey, tagsOutOfProcess } from "./pipelineStatus";

function status(key: string, overrides: Partial<PipelineStatus> = {}): PipelineStatus {
  return { id: key, key, label: key, colour: "neutral", position: 1, isDefault: false, outOfProcess: false, retired: false, ...overrides };
}

const CATALOG: PipelineCatalog = {
  stages: [
    {
      id: "s1", key: "research", label: "Research", colour: "neutral", position: 1, retired: false,
      statuses: [
        status("identified", { label: "Identified", isDefault: true, retired: true }),
        status("source", { label: "Source", position: 2, isDefault: true }),
        status("offLimits", { label: "Off-limits", position: 3, outOfProcess: true }),
      ],
    },
    {
      id: "s2", key: "approach", label: "Approach", colour: "violet", position: 2, retired: false,
      statuses: [status("contacted", { label: "Awaiting reply", isDefault: true })],
    },
    {
      id: "s3", key: "old", label: "Old", colour: "red", position: 3, retired: true,
      statuses: [status("legacy", { label: "Legacy" })],
    },
  ],
};

describe("the workspace's pipeline in the panel", () => {
  it("starts someone at the first live stage's default in process, past a retired one", () => {
    expect(startingStatusKey(CATALOG)).toBe("source");
  });

  it("offers live stages and statuses only, keeping a retired one the person still holds", () => {
    expect(pickableGroups(CATALOG, "", null).map((group) => group.statuses.map((each) => each.key))).toEqual([
      ["source", "offLimits"],
      ["contacted"],
    ]);
    expect(pickableGroups(CATALOG, "", "legacy").map((group) => group.stage.key)).toEqual(["research", "approach", "old"]);
  });

  it("finds a status by its own label or its stage's", () => {
    expect(pickableGroups(CATALOG, "awaiting", null).flatMap((group) => group.statuses.map((each) => each.key))).toEqual([
      "contacted",
    ]);
    expect(pickableGroups(CATALOG, "research", null)[0].statuses).toHaveLength(2);
  });

  it("marks a way out only beside statuses still in process", () => {
    const research = CATALOG.stages[0];
    expect(tagsOutOfProcess(research, research.statuses[2])).toBe(true);
    expect(tagsOutOfProcess({ statuses: [status("gone", { outOfProcess: true })] }, { outOfProcess: true })).toBe(false);
  });
});
