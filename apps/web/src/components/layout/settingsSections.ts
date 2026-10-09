import type { User } from "../../features/auth/api/types";
import { isPureClient } from "../../features/auth/roles";
import { ICONS } from "./Icon";

/**
 * The settings sections that exist, in the mockup's groups.
 *
 * One table drives the rail, the breadcrumb and where `/settings` opens, so a section cannot appear in the rail
 * under one name and in the header under another. The mockup's Notifications is absent until its screen is built:
 * an item that leads nowhere is worse than no item. Team lives at `/team`, the one roster, and is listed here so an
 * admin looking for it in Settings finds it.
 */
export type SettingsGroupLabel = "Your account" | "Workspace" | "Platform";

export interface SettingsSection {
  to: string;
  label: string;
  icon: string;
  group: SettingsGroupLabel;
  /** In Your account, but not a pure client's. */
  staffOnly?: boolean;
  /** In Workspace, and every staff member's rather than only an admin's. */
  everyStaff?: boolean;
  /** Leads out of Settings; said beside the item. */
  leavesShell?: string;
}

export const SETTINGS_SECTIONS: readonly SettingsSection[] = [
  { to: "/settings/profile", label: "Profile", icon: ICONS.profile, group: "Your account" },
  { to: "/settings/security", label: "Security", icon: ICONS.lock, group: "Your account" },
  { to: "/settings/workspaces", label: "Workspaces", icon: ICONS.allProjects, group: "Your account" },
  { to: "/settings/api-keys", label: "API keys", icon: ICONS.key, group: "Your account", staffOnly: true },
  { to: "/settings/ai-apps", label: "Connected AI apps", icon: ICONS.sparkle, group: "Your account", staffOnly: true },
  { to: "/settings/general", label: "General", icon: ICONS.settings, group: "Workspace" },
  {
    to: "/team",
    label: "Team",
    icon: ICONS.members,
    group: "Workspace",
    everyStaff: true,
    leavesShell: "Opens the Team page, outside Settings",
  },
  { to: "/settings/candidate-tags", label: "Candidate tags", icon: ICONS.tag, group: "Workspace" },
  { to: "/settings/integrations", label: "Integrations", icon: ICONS.plug, group: "Workspace" },
  { to: "/settings/billing", label: "Billing", icon: ICONS.card, group: "Workspace", everyStaff: true },
  { to: "/settings/templates", label: "Templates", icon: ICONS.file, group: "Workspace" },
  { to: "/settings/template-library", label: "Template library", icon: ICONS.position, group: "Platform" },
];

/**
 * The sections this caller reaches. Your account is everyone's — a portal guest has a name and a timezone like
 * anyone else — except API keys and Connected AI apps, which are staff's. Workspace is admin-only but for Team and
 * Billing, which every staff member reads, and Platform is Uncava staff's. Hiding them is presentation; the route
 * guards are the gate.
 */
export function visibleSettingsSections(user: User | null): SettingsSection[] {
  const roles = user?.workspace?.roles ?? [];
  const isAdmin = roles.includes("ADMIN");
  const isClient = isPureClient(roles);
  const isLibraryEditor = user?.platformActions.includes("TEMPLATE_LIBRARY_MANAGE") ?? false;
  return SETTINGS_SECTIONS.filter((section) => {
    if (section.group === "Platform") return isLibraryEditor;
    if (section.group === "Workspace") return !isClient && (isAdmin || !!section.everyStaff);
    return !(isClient && section.staffOnly);
  });
}

/** The section a path belongs to, if it is one of Settings' own pages. */
export function settingsSectionOf(pathname: string): SettingsSection | undefined {
  return SETTINGS_SECTIONS.find(
    (section) =>
      section.to.startsWith("/settings/") && (pathname === section.to || pathname.startsWith(`${section.to}/`)),
  );
}

const lastSectionKey = (user: User) => `lightmove.settings.lastSection.${user.id}`;

export function rememberSettingsSection(user: User | null, pathname: string): void {
  const section = settingsSectionOf(pathname);
  // A section typed in that the guard is about to bounce is not one to come back to.
  if (!user || !section || !visibleSettingsSections(user).includes(section)) return;
  try {
    localStorage.setItem(lastSectionKey(user), section.to);
  } catch {
    // Storage can be blocked; Settings then opens on the default section, which is still somewhere sensible.
  }
}

/**
 * Where `/settings` opens: the section last visited, while the caller can still reach it; otherwise General for an
 * admin, whose reason to open Settings is usually the workspace, and Profile for everyone else.
 */
export function settingsLandingOf(user: User | null): string {
  const visible = visibleSettingsSections(user);
  let remembered: string | null = null;
  try {
    remembered = user ? localStorage.getItem(lastSectionKey(user)) : null;
  } catch {
    remembered = null;
  }
  if (remembered && visible.some((section) => section.to === remembered)) return remembered;
  return user?.workspace?.roles.includes("ADMIN") ? "/settings/general" : "/settings/profile";
}
