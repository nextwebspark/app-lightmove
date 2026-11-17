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

  it("puts a status under its stage's heading when another stage has a status of the same name", () => {
    const catalog = aCurrentCatalog();
    const offer = catalog.stages.find((stage) => stage.key === "shortlisted")!;
    offer.statuses.push({ ...offer.statuses[0], id: "withdrew-offer", key: "withdrewOffer", label: "Withdrew", outOfProcess: true, isDefault: false });

    expect(executiveStatusOptions(catalog, new Set(["withdrew", "withdrewOffer"]))).toEqual([
      { value: "stage:interested", label: "All Assessment" },
      { value: "withdrew", label: "Withdrew · Out of process", indent: true },
      { value: "stage:shortlisted", label: "All Offer" },
      { value: "withdrewOffer", label: "Withdrew · Out of process", indent: true },
    ]);
  });

  it("never tells a status apart from a retired one of the same name", () => {
    const catalog = aCurrentCatalog();
    const offer = catalog.stages.find((stage) => stage.key === "shortlisted")!;
    offer.statuses.push({ ...offer.statuses[0], id: "old-withdrew", key: "oldWithdrew", label: "Withdrew", retired: true, isDefault: false });

    expect(executiveStatusOptions(catalog, new Set(["withdrew"]))).toEqual([
      { value: "withdrew", label: "Withdrew · Out of process", indent: false },
    ]);
  });

  it("sends a ticked stage as every status under it", () => {
    expect(statusKeysOf(["stage:contacted", "offLimits"], aSeededCatalog()).sort()).toEqual(
      ["callBooked", "contacted", "engaged", "followUpSent", "offLimits"].sort(),
    );
  });
});
