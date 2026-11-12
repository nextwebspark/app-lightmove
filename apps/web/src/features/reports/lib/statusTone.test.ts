import { describe, expect, it } from "vitest";
import { aCurrentCatalog, aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { sliceInterest } from "./marketStats";
import { stageTone, statusTone } from "./statusTone";

describe("the report's four readings", () => {
  it("reads a row by its stage, so every closed status is a closed door — out of scope included", () => {
    const catalog = aSeededCatalog();
    expect(statusTone({ status: "outOfScope" }, catalog)).toBe("closed");
    expect(statusTone({ status: "withdrew" }, catalog)).toBe("closed");
    expect(statusTone({ status: "researching" }, catalog)).toBe("untouched");
    expect(statusTone({ status: "callBooked" }, catalog)).toBe("inConversation");
    expect(statusTone({ status: "offer" }, catalog)).toBe("interested");
  });

  it("reads a status out of process as a closed door whatever stage it sits under, the server's word first", () => {
    const catalog = aCurrentCatalog();
    expect(statusTone({ status: "notInterested" }, catalog)).toBe("closed");
    expect(statusTone({ status: "callArranged" }, catalog)).toBe("inConversation");
    expect(
      statusTone(
        { status: "callArranged", statusOutOfProcess: true, pipelineStage: { key: "contacted", position: 2 } },
        catalog,
      ),
    ).toBe("closed");
  });

  it("reads the stage the server sent before the catalog, and the seven built-in keys without one", () => {
    expect(statusTone({ status: "custom", pipelineStage: { key: "assessed", position: 5 } })).toBe(
      "interested",
    );
    expect(statusTone({ status: "engaged" })).toBe("inConversation");
    expect(stageTone("qualified")).toBe("interested");
    expect(stageTone("declined")).toBe("closed");
  });

  it("counts a pocket's interest by stage", () => {
    const executives = ["interested", "screened", "contacted", "identified", "offLimits", "outOfScope"].map(
      (status) => ({ status }),
    );
    expect(sliceInterest(executives, aSeededCatalog())).toEqual({ interested: 2, passive: 2, closed: 2 });
  });
});
