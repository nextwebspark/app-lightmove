import { describe, expect, it } from "vitest";
import type { Project } from "../api/types";
import { businessUnitOf, compareBusinessUnits, NO_BUSINESS_UNIT } from "./grouping";

describe("businessUnitOf", () => {
  it("falls back to the sentinel for the blank name a dropped client record leaves behind", () => {
    expect(businessUnitOf({ clientName: "Automotive" } as Project)).toBe("Automotive");
    expect(businessUnitOf({ clientName: "" } as Project)).toBe(NO_BUSINESS_UNIT);
  });
});

describe("compareBusinessUnits", () => {
  it("orders alphabetically with the sentinel bucket last", () => {
    const sorted = [NO_BUSINESS_UNIT, "Retail", "Automotive"].sort(compareBusinessUnits);
    expect(sorted).toEqual(["Automotive", "Retail", NO_BUSINESS_UNIT]);
  });
});
