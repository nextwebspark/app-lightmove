/**
 * The tabs both person drawers draw, in `Position.dc.html`'s order. A position's drawer shows every one
 * a staff seat may use; the workspace Candidates drawer leaves out what only a position can hold.
 */
export type DrawerTab = "career" | "notes" | "assessment" | "contact" | "documents" | "activity" | "positions";

export const DRAWER_TAB_LABELS: Readonly<Record<DrawerTab, string>> = {
  career: "Career",
  notes: "Notes",
  assessment: "Assessment",
  contact: "Contact",
  documents: "Documents",
  activity: "Activity",
  positions: "Positions",
};

export const POSITION_STAFF_TABS: readonly DrawerTab[] = [
  "career",
  "notes",
  "assessment",
  "contact",
  "documents",
  "activity",
  "positions",
];

export const POSITION_CLIENT_TABS: readonly DrawerTab[] = ["career", "contact"];

export const WORKSPACE_TABS: readonly DrawerTab[] = [
  "career",
  "notes",
  "contact",
  "documents",
  "activity",
  "positions",
];

/** Links written before the split — `?tab=records`, a bell's `tab=timeline` — still land somewhere sensible. */
const RETIRED_TABS: Readonly<Record<string, DrawerTab>> = {
  profile: "career",
  records: "notes",
  timeline: "activity",
};

export function drawerTabOf(value: string | null | undefined, offered: readonly DrawerTab[]): DrawerTab {
  const tab = (offered as readonly string[]).includes(value ?? "")
    ? (value as DrawerTab)
    : RETIRED_TABS[value ?? ""];
  return tab && offered.includes(tab) ? tab : offered[0];
}

export type DrawerKind = "position" | "workspace";

const REMEMBERED_TAB_KEY = (kind: DrawerKind) => `lm.person-drawer.tab.${kind}`;

/** The tab this viewer last left a drawer of this kind on, this session — someone working down a list reads one tab. */
export function rememberedDrawerTab(kind: DrawerKind, offered: readonly DrawerTab[]): DrawerTab {
  try {
    return drawerTabOf(sessionStorage.getItem(REMEMBERED_TAB_KEY(kind)), offered);
  } catch {
    return offered[0];
  }
}

export function rememberDrawerTab(kind: DrawerKind, tab: DrawerTab) {
  try {
    sessionStorage.setItem(REMEMBERED_TAB_KEY(kind), tab);
  } catch {
    // Storage refused (a private window): the drawer simply opens on its first tab next time.
  }
}
