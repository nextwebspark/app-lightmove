import { describe, expect, it } from "vitest";
import type { Report } from "../api/types";
import { nextActionsFor } from "./nextActions";

/**
 * Every action the report offers must answer a gap the report itself just measured — the whole reason
 * these are rules rather than the mockup's written-in advice.
 */
describe("nextActionsFor", () => {
  const scoped: Report = {
    universeCount: 42,
    offLimitsCompanies: 1,
    sectorsInScope: 2,
    marketsInScope: 4,
    sectors: [{ label: "Retail", count: 42 }],
    countries: [{ label: "AE", count: 42 }],
    cities: [{ label: "Dubai", count: 30 }],
    mandateBand: { min: 900000, max: 1300000, currency: "USD" },
    caveats: { revenueBandExcludesUnknown: false },
  };

  const titles = (report: Report) => nextActionsFor(report, "p1").map((action) => action.title);

  it("asks for a scope first when the mandate has none, and not for a wider one", () => {
    const actions = nextActionsFor({ ...scoped, sectorsInScope: 0, universeCount: 0 }, "p1");

    expect(actions[0].title).toBe("Set the search scope");
    expect(actions[0].to).toBe("/projects/p1/strategy");
    // "Widen the scope" over an unset scope would be advice to loosen something never tightened.
    expect(actions.map((action) => action.title)).not.toContain("Widen the scope");
  });

  it("asks for a wider scope when a scope is set but matches nothing", () => {
    expect(titles({ ...scoped, universeCount: 0 })).toContain("Widen the scope");
  });

  it("asks for the compensation band only while it is missing", () => {
    expect(titles(scoped)).not.toContain("State the compensation band");
    expect(titles({ ...scoped, mandateBand: null })).toContain("State the compensation band");
  });

  it("always closes on mapping, since no mandate has an executive yet", () => {
    const actions = nextActionsFor(scoped, "p1");
    const last = actions[actions.length - 1];

    expect(last.title).toBe("Map executives against the universe");
    // No link: the Candidates screen it would point at does not exist.
    expect(last.to).toBeUndefined();
    expect(last.body).toContain("42 companies");
  });
});
