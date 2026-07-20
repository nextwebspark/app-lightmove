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
      "PUBLICLY_LISTED",
      "FAMILY_OWNED_PRIVATE",
      "STATE_LINKED_SOVEREIGN",
      "PE_VC_BACKED",
      "FOREIGN_MULTINATIONAL_SUBSIDIARY",
    ]);
  });

  it("gives every structure a display name", () => {
    for (const structure of OWNERSHIP_STRUCTURES) {
      expect(structure.label).not.toHaveLength(0);
    }
  });
});
