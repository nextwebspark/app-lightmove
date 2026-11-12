import { describe, expect, it } from "vitest";
import { aCurrentCatalog } from "../../../test/fixtures/pipelineCatalog";
import { wouldMoveForward } from "./statusAutomation";

describe("an automation's forward move", () => {
  const catalog = aCurrentCatalog();

  it("moves someone earlier on the pipeline forward to the status chosen", () => {
    expect(wouldMoveForward(catalog, "identified", "status-engaged")).toBe(true);
    expect(wouldMoveForward(catalog, "callArranged", "status-engaged")).toBe(false);
  });

  it("never moves someone out of process, though their status sits before the one chosen", () => {
    expect(wouldMoveForward(catalog, "outOfScope", "status-engaged")).toBe(false);
  });
});
