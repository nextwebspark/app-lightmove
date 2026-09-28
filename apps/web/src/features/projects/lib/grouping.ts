import type { DataGridGrouping } from "../../../components/ui/DataGrid";
import { compareText } from "../../../lib/gridSortFns";
import type { Project } from "../api/types";

/**
 * Positions grouped by who they are for. The server sends `clientName: ""` when the client record is
 * gone; those land in one bucket, named by the caller's vocabulary and ordered last.
 */
export function projectGroupingFor(unassignedLabel: string): DataGridGrouping<Project> {
  return {
    keyOf: (project) => project.clientName || unassignedLabel,
    compare: (a, b) => {
      if (a === unassignedLabel) return b === unassignedLabel ? 0 : 1;
      if (b === unassignedLabel) return -1;
      return compareText(a, b);
    },
  };
}
