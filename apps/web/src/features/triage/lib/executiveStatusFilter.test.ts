import { describe, expect, it } from "vitest";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { executiveStatusOptions, statusKeysOf } from "./executiveStatusFilter";

describe("the Status column's filter", () => {
  it("offers the statuses executives hold under their stage, and the stage itself where it holds several", () => {
    expect(executiveStatusOptions(aSeededCatalog(), new Set(["identified", "contacted", "engaged", "offLimits"]))).toEqual([
      { value: "identified", label: "Identified", indent: false },
      { value: "stage:contacted", label: "All Contacted" },
      { value: "contacted", label: "Contacted", indent: true },
      { value: "engaged", label: "Engaged", indent: true },
      { value: "offLimits", label: "Off-limits", indent: false },
    ]);
  });

  it("sends a ticked stage as every status under it", () => {
    expect(statusKeysOf(["stage:contacted", "offLimits"], aSeededCatalog()).sort()).toEqual(
      ["callBooked", "contacted", "engaged", "followUpSent", "offLimits"].sort(),
    );
  });
});
