/**
 * The tabs both person drawers draw, in `Position.dc.html`'s order, grouped by what the consultant is doing: who the
 * person is (Profile: background, package and documents as folds), what the team thinks (Notes), how they score
 * (Assessment), how they are being approached (Outreach: the run and the calls) and what happened (Activity). The
 * contacts are the header's, on every tab. `copilot` is in no list: a position's drawer puts it first only while a
 * call's live copilot runs.
 */
export type DrawerTab = "copilot" | "profile" | "notes" | "assessment" | "outreach" | "activity" | "positions";

export const DRAWER_TAB_LABELS: Readonly<Record<DrawerTab, string>> = {
  copilot: "Copilot",
  profile: "Profile",
  notes: "Notes",
  assessment: "Assessment",
  outreach: "Outreach",
  activity: "Activity",
  positions: "Positions",
};

export const POSITION_STAFF_TABS: readonly DrawerTab[] = ["profile", "notes", "assessment", "outreach", "activity"];

/** A client seat reads who the executive is; how to reach them is the header's, and the package is the firm's. */
export const POSITION_CLIENT_TABS: readonly DrawerTab[] = ["profile"];

/** The workspace drawer reads; what only a position holds (its scorecard, its outreach) is that position's drawer's. */
export const WORKSPACE_TABS: readonly DrawerTab[] = ["profile", "notes", "activity", "positions"];

/**
 * Links written before a rename or a regrouping — `?tab=records`, a bell's `tab=timeline`, a remembered `contact` —
 * still land somewhere sensible. Background, Compensation and Documents are Profile's folds now; Contact's outreach
 * and calls are the Outreach tab's.
 */
const RETIRED_TABS: Readonly<Record<string, DrawerTab>> = {
  background: "profile",
  career: "profile",
  compensation: "profile",
  documents: "profile",
  contact: "outreach",
  records: "notes",
  timeline: "activity",
};


export function drawerTabOf(value: string | null | undefined, offered: readonly DrawerTab[]): DrawerTab {
  const tab = (offered as readonly string[]).includes(value ?? "")
    ? (value as DrawerTab)
    : RETIRED_TABS[value ?? ""];
  return tab && offered.includes(tab) ? tab : offered[0];
}

/**
 * Where a link may open the drawer: `contact` the contact details window (a bounce's, or Book a call stopped for want
 * of an address), `outreach` and `meetings` the Outreach tab's parts (a reply, a booked call).
 */
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
