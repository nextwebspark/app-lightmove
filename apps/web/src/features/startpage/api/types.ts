/** Where Uncava opens in a workspace. HOME is the default and the answer when nothing is saved. */
export type StartPageKind = "HOME" | "POSITIONS" | "CANDIDATES" | "TASKS" | "DASHBOARD" | "POSITION";

/** The tab of a position a start page opens on. */
export type PositionSection = "BRIEF" | "STRATEGY" | "UNIVERSE" | "OUTREACH" | "DASHBOARD";

export interface StartPage {
  kind: StartPageKind;
  dashboardId: string | null;
  projectId: string | null;
  section: PositionSection | null;
  /** False when the saved page is no longer the member's to open: Home opens instead, and says so. */
  available: boolean;
}

export interface SaveStartPage {
  kind: StartPageKind;
  dashboardId?: string;
  projectId?: string;
  section?: PositionSection;
}
