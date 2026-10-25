/**
 * The tabs both person drawers draw, in `Position.dc.html`'s order. A position's drawer shows every one
 * a staff seat may use; the workspace Candidates drawer leaves out what only a position can hold. `copilot` is in
 * no list: a position's drawer puts it first only while a call's live copilot runs.
 */
export type DrawerTab =
  | "copilot"
  | "career"
  | "notes"
  | "assessment"
  | "contact"
  | "documents"
  | "activity"
  | "positions";

export const DRAWER_TAB_LABELS: Readonly<Record<DrawerTab, string>> = {
  copilot: "Copilot",
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

/** The position drawer's own tabs, less what only a position holds — so a new tab reaches both drawers. */
export const WORKSPACE_TABS: readonly DrawerTab[] = POSITION_STAFF_TABS.filter((tab) => tab !== "assessment");

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

/** A part of the Contact tab a link may open on: a bell row's bounce, reply or booked call. */
export type DrawerSection = "contact" | "outreach" | "meetings";

const DRAWER_SECTIONS: readonly DrawerSection[] = ["contact", "outreach", "meetings"];

export function drawerSectionOf(value: string | null | undefined): DrawerSection | null {
  return (DRAWER_SECTIONS as readonly string[]).includes(value ?? "") ? (value as DrawerSection) : null;
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

/** The copilot is never remembered: it lasts one call, and the next profile has none. */
export function rememberDrawerTab(kind: DrawerKind, tab: DrawerTab) {
  if (tab === "copilot") return;
  try {
    sessionStorage.setItem(REMEMBERED_TAB_KEY(kind), tab);
  } catch {
    // Storage refused (a private window): the drawer simply opens on its first tab next time.
  }
}
