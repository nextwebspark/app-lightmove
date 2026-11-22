import type { PositionSection, StartPage, StartPageKind } from "../api/types";

const SECTION_PATH: Record<PositionSection, string> = {
  BRIEF: "",
  STRATEGY: "/strategy",
  UNIVERSE: "/companies/universe",
  OUTREACH: "/outreach",
  DASHBOARD: "/reports",
};

/** Where a start page opens; Home for none, and for one the member can no longer open. */
export function startPagePath(startPage: StartPage | null | undefined): string {
  if (!startPage?.available) return "/";
  switch (startPage.kind) {
    case "POSITIONS":
      return "/positions";
    case "CANDIDATES":
      return "/candidates";
    case "TASKS":
      return "/tasks";
    case "DASHBOARD":
      return startPage.dashboardId ? `/dashboards?dashboard=${encodeURIComponent(startPage.dashboardId)}` : "/";
    case "POSITION":
      return startPage.projectId
        ? `/projects/${encodeURIComponent(startPage.projectId)}${SECTION_PATH[startPage.section ?? "BRIEF"]}`
        : "/";
    default:
      return "/";
  }
}

export interface StartPageOption {
  kind: StartPageKind;
  label: string;
  hint: string;
  staffOnly: boolean;
}

export const START_PAGE_OPTIONS: StartPageOption[] = [
  { kind: "HOME", label: "Home", hint: "What needs you today", staffOnly: false },
  { kind: "POSITIONS", label: "Positions", hint: "The list of positions", staffOnly: false },
  { kind: "CANDIDATES", label: "Candidates", hint: "Everyone your firm has mapped", staffOnly: true },
  { kind: "TASKS", label: "Tasks", hint: "Your tasks, due and coming up", staffOnly: true },
  { kind: "DASHBOARD", label: "One of your dashboards", hint: "Yours or one shared with you", staffOnly: true },
  { kind: "POSITION", label: "A position", hint: "One you are on, on the tab you choose", staffOnly: false },
];

export const POSITION_SECTIONS: { section: PositionSection; label: string; staffOnly: boolean }[] = [
  { section: "BRIEF", label: "Brief", staffOnly: false },
  { section: "STRATEGY", label: "Strategy", staffOnly: false },
  { section: "UNIVERSE", label: "In universe", staffOnly: false },
  { section: "OUTREACH", label: "Outreach", staffOnly: true },
  { section: "DASHBOARD", label: "Dashboard", staffOnly: false },
];

/** The start page in a few words, for Home's Options. */
export function startPageLabel(startPage: StartPage | null | undefined): string {
  if (!startPage?.available) return "Home";
  return START_PAGE_OPTIONS.find((option) => option.kind === startPage.kind)?.label ?? "Home";
}
