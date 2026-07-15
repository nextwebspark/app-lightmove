import type { Project, ProjectStage } from "../api/types";

/**
 * The workspace home's list logic — My/All, stage chips, search, sort — extracted pure so the
 * combination the whole screen hangs on is testable without a DOM.
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
export type SortKey = "client" | "stage" | "date";

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

export function sortProjects(projects: Project[], key: SortKey, direction: 1 | -1): Project[] {
  return [...projects].sort((a, b) => {
    let left: string | number;
    let right: string | number;
    if (key === "client") {
      // localeCompare, or "Zeta" sorts before "apple".
      return a.clientName.localeCompare(b.clientName) * direction;
    } else if (key === "stage") {
      left = STAGE_ORDER.indexOf(a.stage);
      right = STAGE_ORDER.indexOf(b.stage);
    } else {
      // Undated projects sort last, whichever way the dated ones go.
      left = a.targetDate ?? "9999-12-31";
      right = b.targetDate ?? "9999-12-31";
    }
    return (left < right ? -1 : left > right ? 1 : 0) * direction;
  });
}
