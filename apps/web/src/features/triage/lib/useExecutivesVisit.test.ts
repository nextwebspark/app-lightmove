import { describe, expect, it } from "vitest";
import { executivesEntryOf } from "./useExecutivesVisit";

describe("executivesEntryOf", () => {
  it("reads the entry Open full page pushed", () => {
    expect(executivesEntryOf({ from: "pipeline", enteredFor: "c1" })).toEqual({ from: "pipeline", enteredFor: "c1" });
  });

  it("takes any other router state as no entry, rather than a view it cannot name", () => {
    expect(executivesEntryOf(null)).toBeNull();
    expect(executivesEntryOf("table")).toBeNull();
    expect(executivesEntryOf({ positionsOrigin: "/projects" })).toBeNull();
    expect(executivesEntryOf({ from: "map", enteredFor: "c1" })).toBeNull();
    expect(executivesEntryOf({ from: "table" })).toBeNull();
  });
});
