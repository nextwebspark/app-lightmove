import { describe, expect, it } from "vitest";
import type { Project } from "../api/types";
import { projectGroupingFor } from "./grouping";

const grouping = projectGroupingFor("No client");

describe("projectGroupingFor", () => {
  it("files the blank name a dropped client record leaves behind under the caller's label", () => {
    expect(grouping.keyOf({ clientName: "Automotive" } as Project)).toBe("Automotive");
    expect(grouping.keyOf({ clientName: "" } as Project)).toBe("No client");
  });

  it("orders alphabetically with the unassigned bucket last", () => {
    const sorted = ["No client", "Retail", "Automotive"].sort(grouping.compare);
    expect(sorted).toEqual(["Automotive", "Retail", "No client"]);
  });
});
