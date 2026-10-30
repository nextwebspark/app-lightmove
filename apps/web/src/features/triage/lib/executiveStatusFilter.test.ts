import { describe, expect, it } from "vitest";
import { aCurrentCatalog, aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
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

  it("says which statuses under an active stage are ways out of the process", () => {
    expect(executiveStatusOptions(aCurrentCatalog(), new Set(["contacted", "notInterested"]))).toEqual([
      { value: "stage:contacted", label: "All Approach" },
      { value: "contacted", label: "Awaiting reply", indent: true },
      { value: "notInterested", label: "Not interested · Out of process", indent: true },
    ]);
  });

  it("sends a ticked stage as every status under it", () => {
    expect(statusKeysOf(["stage:contacted", "offLimits"], aSeededCatalog()).sort()).toEqual(
      ["callBooked", "contacted", "engaged", "followUpSent", "offLimits"].sort(),
    );
  });
});
