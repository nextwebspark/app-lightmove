import type { DataGridGrouping } from "../../../components/ui/DataGrid";
import { compareText } from "../../../lib/gridSortFns";
import type { Project } from "../api/types";

/** The server sends `clientName: ""` when the client record is gone; this bucket catches it. */
export const NO_BUSINESS_UNIT = "No business unit";

export function businessUnitOf(project: Project): string {
  return project.clientName || NO_BUSINESS_UNIT;
}

export function compareBusinessUnits(a: string, b: string): number {
  if (a === NO_BUSINESS_UNIT) return b === NO_BUSINESS_UNIT ? 0 : 1;
  if (b === NO_BUSINESS_UNIT) return -1;
  return compareText(a, b);
}

export const PROJECT_GROUPING: DataGridGrouping<Project> = {
  keyOf: businessUnitOf,
  compare: compareBusinessUnits,
};
