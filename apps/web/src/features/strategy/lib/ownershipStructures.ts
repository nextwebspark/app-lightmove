/**
 * The ownership structure catalog — a client-side mirror of the backend OwnershipStructure enum.
 * `value` is the org_type string (what a PUT sends and what the response echoes — also the company
 * universe's app_lm_companies.org_type join key); `label` is the display name the chips render.
 * Only the org_type buckets the company universe actually holds are listed, in descending frequency.
 * ownershipStructures.test.ts guards the two catalogs against drift.
 */

import type { CatalogOption } from "./catalogOption";

export const OWNERSHIP_STRUCTURES: CatalogOption[] = [
  { value: "Privately Held", label: "Privately Held" },
  { value: "Partnership", label: "Partnership" },
  { value: "Public Company", label: "Public Company" },
  { value: "Self-Owned", label: "Self-Owned" },
  { value: "Educational", label: "Educational" },
  { value: "Self-Employed", label: "Self-Employed" },
  { value: "Government Agency", label: "Government Agency" },
  { value: "Nonprofit", label: "Nonprofit" },
];
