import type { Project, ProjectRole, ProjectStage } from "../api/types";

/**
 * The Positions list's logic — stage chips and search — extracted pure so the combination
 * the whole screen hangs on is testable without a DOM. Ordering is the grid's: the columns declare
 * their own comparators and the table sorts the rows it was handed.
 */

export const STAGE_ORDER: ProjectStage[] = [
  "BRIEF",
  "UNIVERSE",
  "LOCKED",
  "MAPPING",
  "OUTREACH",
  "DELIVERED",
  "CLOSED",
];

export const CHIPS = [
  { key: "active", label: "Active" },
  { key: "allstages", label: "All stages" },
  { key: "UNIVERSE", label: "Universe" },
  { key: "MAPPING", label: "Mapping" },
  { key: "OUTREACH", label: "Outreach" },
  { key: "DELIVERED", label: "Delivered" },
  { key: "archived", label: "Archived" },
] as const;

export type ChipKey = (typeof CHIPS)[number]["key"];

export function isArchived(project: Project): boolean {
  return Boolean(project.archivedAt);
}

/** Every position but the archived ones: what a list, a picker or a count offers. */
export function livePositions(projects: Project[]): Project[] {
  return projects.filter((project) => !isArchived(project));
}

export function isActive(project: Project): boolean {
  return !isArchived(project) && project.stage !== "DELIVERED" && project.stage !== "CLOSED";
}

/** An active position the user holds a seat on, as `role` when one is named. */
export function isSeatedOn(project: Project, userId: string | undefined, role?: ProjectRole): boolean {
  return (
    isActive(project) &&
    project.team.some((seat) => seat.userId === userId && (role === undefined || seat.projectRoles.includes(role)))
  );
}

export function filterProjects(
  projects: Project[],
  options: { chip: ChipKey; query: string },
): Project[] {
  const query = options.query.trim().toLowerCase();

  return projects.filter((project) => {
    if ((options.chip === "archived") !== isArchived(project)) return false;
    if (options.chip === "active" && !isActive(project)) return false;
    if (
      options.chip !== "active" &&
      options.chip !== "allstages" &&
      options.chip !== "archived" &&
      project.stage !== options.chip
    ) {
      return false;
    }
    if (query && !`${project.clientName} ${project.positionTitle}`.toLowerCase().includes(query)) {
      return false;
    }
    return true;
  });
}
