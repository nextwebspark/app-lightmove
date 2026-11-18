import { describe, expect, it, vi } from "vitest";
import type { DataGridCheckFilter } from "../../../lib/gridFilters";
import { LEGACY_CATALOG } from "./pipelineStatus";
import { NO_POOL_FILTERS, pickedView, poolColumnFilters, type PoolFilterSources } from "./poolFilters";

function showOptions(gates: Pick<PoolFilterSources, "showsPrivacyViews" | "showsConsentViews">): string[] {
  const filters = poolColumnFilters(NO_POOL_FILTERS, vi.fn(), { name: "", title: "", company: "" }, vi.fn(), {
    tags: [],
    staff: [],
    positions: [],
    countries: [],
    catalog: LEGACY_CATALOG,
    ...gates,
  });
  return (filters.view as DataGridCheckFilter).options.map((option) => option.value);
}

describe("the Show filter", () => {
  it("answers one view: a tick from everyone, a second tick replacing the first, an untick back to everyone", () => {
    expect(pickedView(["mine"], "all")).toBe("mine");
    expect(pickedView(["mine", "unplaced"], "mine")).toBe("unplaced");
    expect(pickedView([], "mine")).toBe("all");
    expect(pickedView(["mine"], "mine")).toBe("mine");
  });

  it("keeps the privacy and consent views hidden unless the workspace offers them", () => {
    expect(showOptions({ showsPrivacyViews: false, showsConsentViews: false })).toEqual(["mine", "active", "unplaced"]);
    expect(showOptions({ showsPrivacyViews: true, showsConsentViews: false })).toContain("not_notified");
    expect(showOptions({ showsPrivacyViews: false, showsConsentViews: true })).toEqual([
      "mine",
      "active",
      "unplaced",
      "consent_asked",
      "consent_ending",
      "consent_refused",
    ]);
  });
});
