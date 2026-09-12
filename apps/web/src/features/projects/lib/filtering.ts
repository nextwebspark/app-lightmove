import type { Project, ProjectStage } from "../api/types";

/**
 * The workspace home's list logic — My/All, stage chips, search — extracted pure so the combination
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
] as const;

export type ChipKey = (typeof CHIPS)[number]["key"];

export function isActive(project: Project): boolean {
  return project.stage !== "DELIVERED" && project.stage !== "CLOSED";
}

export function filterProjects(
  projects: Project[],
  options: { view: "my" | "all"; myMemberId?: string; chip: ChipKey; query: string },
): Project[] {
  const query = options.query.trim().toLowerCase();

  return projects.filter((project) => {
    if (options.view === "my" && !project.team.some((seat) => seat.memberId === options.myMemberId)) {
      return false;
    }
    if (options.chip === "active" && !isActive(project)) return false;
    if (options.chip !== "active" && options.chip !== "allstages" && project.stage !== options.chip) {
      return false;
    }
    if (query && !`${project.clientName} ${project.positionTitle}`.toLowerCase().includes(query)) {
      return false;
    }
    return true;
  });
}
