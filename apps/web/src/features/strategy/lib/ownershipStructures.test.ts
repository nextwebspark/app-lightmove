import { describe, expect, it } from "vitest";
import { OWNERSHIP_STRUCTURES } from "./ownershipStructures";

/**
 * Drift guard: the frontend structure catalog must stay in step with the backend OwnershipStructure
 * enum. If the backend gains or renames a structure, this red test flags the mirror before a PUT
 * silently fails validation on an unknown value.
 */
describe("ownership structure catalog", () => {
  it("carries exactly the backend structure values, in catalog order", () => {
    expect(OWNERSHIP_STRUCTURES.map((structure) => structure.value)).toEqual([
      "Privately Held",
      "Partnership",
      "Public Company",
      "Self-Owned",
      "Educational",
      "Self-Employed",
      "Government Agency",
      "Nonprofit",
    ]);
  });

  it("gives every structure a display name", () => {
    for (const structure of OWNERSHIP_STRUCTURES) {
      expect(structure.label).not.toHaveLength(0);
    }
  });
});
