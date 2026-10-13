import { describe, expect, it } from "vitest";
import { aSeededCatalog } from "../../../test/fixtures/pipelineCatalog";
import { statusStyle } from "./pipelineStatus";

describe("a status as it draws", () => {
  it("keeps the seven built-in statuses in the colours they always had", () => {
    expect(statusStyle("identified").className).toBe("text-u-text2 bg-u-raised");
    expect(statusStyle("contacted").className).toBe("text-u-accent bg-u-accent-tint");
    expect(statusStyle("interested").className).toBe("text-u-direct bg-u-direct-tint");
    expect(statusStyle("offLimits").className).toBe("text-u-offlimits bg-u-offlimits-tint");
    expect(statusStyle("outOfScope").className).toBe("text-u-signal bg-u-signal-tint");
  });

  it("prefers the row's own label and colour, so a retired or renamed status still draws as it is held", () => {
    expect(statusStyle("researching", { statusLabel: "Digging in", statusColour: "violet" }, aSeededCatalog())).toEqual({
      label: "Digging in",
      colour: "violet",
      className: "text-u-chart-5 bg-u-chart-5/15",
    });
  });

  it("falls back to the catalog, then to the key itself in neutral", () => {
    expect(statusStyle("callBooked", null, aSeededCatalog()).label).toBe("Call booked");
    expect(statusStyle("assessed", null, aSeededCatalog())).toMatchObject({ label: "assessed", colour: "neutral" });
    expect(statusStyle("engaged", { statusColour: "mauve" }).className).toBe("text-u-text2 bg-u-raised");
  });
});
