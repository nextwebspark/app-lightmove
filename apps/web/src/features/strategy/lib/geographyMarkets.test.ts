import { describe, expect, it } from "vitest";
import { GEOGRAPHY_MARKETS } from "./geographyMarkets";

/**
 * Drift guard: the frontend market catalog must stay in step with the backend GeographyMarket enum
 * (whose values are the ISO codes in the company universe's hq_country column). If the backend gains
 * or renames a market, this red test flags the mirror before a PUT silently fails validation on an
 * unknown value.
 */
describe("geography market catalog", () => {
  it("carries exactly the backend market values, in catalog order", () => {
    expect(GEOGRAPHY_MARKETS.map((market) => market.value)).toEqual([
      "AE",
      "SA",
      "KW",
      "QA",
      "BH",
      "OM",
    ]);
  });

  it("gives every market a display name", () => {
    for (const market of GEOGRAPHY_MARKETS) {
      expect(market.label).not.toHaveLength(0);
    }
  });
});
