/**
 * The ownership structure catalog — a client-side mirror of the backend OwnershipStructure enum.
 * `value` is the enum constant name (the stable wire token); `label` is the display name from the
 * mockup, free to be reworded without touching the API. ownershipStructures.test.ts guards the two
 * catalogs against drift.
 */

import type { CatalogOption } from "./catalogOption";

export const OWNERSHIP_STRUCTURES: CatalogOption[] = [
  { value: "PUBLICLY_LISTED", label: "Publicly listed" },
  { value: "FAMILY_OWNED_PRIVATE", label: "Family-owned / private" },
  { value: "STATE_LINKED_SOVEREIGN", label: "State-linked / sovereign" },
  { value: "PE_VC_BACKED", label: "PE or VC-backed" },
  { value: "FOREIGN_MULTINATIONAL_SUBSIDIARY", label: "Subsidiary of foreign multinational" },
];
