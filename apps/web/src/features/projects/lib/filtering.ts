import type { Project, ProjectStage } from "../api/types";

/**
 * The workspace home's list logic — My/All, chips, search — extracted pure so the combination the
 * whole screen hangs on is testable without a DOM. Ordering is the grid's: the columns declare their
 * own comparators and the table sorts the rows it was handed.
 *
 * <p>The chips narrow by what a mandate is and how it is doing, not by its stage: nothing in the app
 * moves a mandate's stage, so a stage chip matched nothing but BRIEF however long a search ran.
 * {@link STAGE_ORDER} stays for the drawer's stage-gate ladder, which is a picture of the pipeline
 * rather than a filter.
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
  { key: "all", label: "All" },
  { key: "MAPPING", label: "Mapping only" },
  { key: "EXECUTIVE_SEARCH", label: "Executive search" },
  { key: "attention", label: "Needs attention" },
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
    if (options.chip === "attention" && project.health !== "RISK" && project.health !== "OFF") {
      return false;
    }
    if (
      (options.chip === "MAPPING" || options.chip === "EXECUTIVE_SEARCH") &&
      project.projectType !== options.chip
    ) {
      return false;
    }
    if (query && !`${project.clientName} ${project.positionTitle}`.toLowerCase().includes(query)) {
      return false;
    }
    return true;
  });
}
