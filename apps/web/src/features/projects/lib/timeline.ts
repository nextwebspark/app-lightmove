import { addDays, daysBetween } from "../../../lib/dates";
import type { ProjectType } from "../api/types";

/**
 * How much of the window to the shortlist the mapping half is given. Mirrors the server's own rule
 * in `MandateTimeline` — both round half up, so the date previewed while the modal is open is the
 * date that gets saved.
 */
const MAPPING_SHARE_OF_WINDOW = 0.6;

/** Where the mapping target lands when a search states only when its shortlist is due. */
export function autoMappingTarget(startIso: string, shortlistIso: string): string {
  return addDays(startIso, Math.round(daysBetween(startIso, shortlistIso) * MAPPING_SHARE_OF_WINDOW));
}

/** Days from the mandate's start to a date on its timeline; null when either end is missing. */
export function daysFromStart(startIso: string, targetIso: string | null): number | null {
  return targetIso ? daysBetween(startIso, targetIso) : null;
}

/**
 * Why a stated timeline cannot be saved, in the words the field shows — the same three rules the
 * server holds every write to, checked here so a mistake is answered before the request.
 */
export function timelineProblem(
  projectType: ProjectType,
  startIso: string,
  mappingTargetIso: string,
  shortlistTargetIso: string,
): { field: "mappingTargetDate" | "shortlistTargetDate"; message: string } | null {
  if (projectType === "EXECUTIVE_SEARCH" && !shortlistTargetIso) {
    return { field: "shortlistTargetDate", message: "Enter the date the shortlist is due" };
  }
  if (mappingTargetIso && startIso && daysBetween(startIso, mappingTargetIso) < 0) {
    return {
      field: "mappingTargetDate",
      message: "The mapping date cannot fall before the project starts",
    };
  }
  if (
    projectType === "EXECUTIVE_SEARCH" &&
    mappingTargetIso &&
    shortlistTargetIso &&
    daysBetween(mappingTargetIso, shortlistTargetIso) < 0
  ) {
    return {
      field: "shortlistTargetDate",
      message: "The shortlist is due before the mapping it draws on",
    };
  }
  return null;
}
