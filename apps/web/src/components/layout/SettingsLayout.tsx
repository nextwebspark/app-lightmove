import { Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { isPureClient } from "../../features/auth/roles";
import { AppShell } from "./AppShell";
import { ICONS } from "./Icon";
import { type SidebarGroup } from "./Sidebar";
import { SettingsBreadcrumb } from "./Topbar";

/**
 * The settings sections that exist, in the mockup's groups.
 *
 * One table drives both the sidebar and the breadcrumb, so a section cannot appear in the rail under
 * one name and in the header under another — which is what the two-way ternary this replaced allowed.
 * The mockup's Notifications is absent until its screen is built: an item that leads nowhere is
 * worse than no item.
 */
const SETTINGS_SECTIONS = [
  { to: "/settings/profile", label: "Profile", icon: ICONS.profile, group: "Account" },
  { to: "/settings/security", label: "Security", icon: ICONS.lock, group: "Account" },
  { to: "/settings/workspaces", label: "Workspaces", icon: ICONS.allProjects, group: "Account" },
  { to: "/settings/api-keys", label: "API keys", icon: ICONS.key, group: "Account", staffOnly: true },
  { to: "/settings/ai-apps", label: "Connected AI apps", icon: ICONS.sparkle, group: "Account", staffOnly: true },
  { to: "/settings/general", label: "General", icon: ICONS.settings, group: "Workspace" },
  { to: "/settings/members", label: "Members", icon: ICONS.members, group: "Workspace" },
  { to: "/settings/candidate-tags", label: "Candidate tags", icon: ICONS.tag, group: "Workspace" },
  { to: "/settings/integrations", label: "Integrations", icon: ICONS.plug, group: "Workspace" },
  { to: "/settings/billing", label: "Billing", icon: ICONS.card, group: "Workspace", everyStaff: true },
  { to: "/settings/templates", label: "Templates", icon: ICONS.file, group: "Workspace" },
  { to: "/settings/template-library", label: "Template library", icon: ICONS.position, group: "Platform" },
] as const;

type SettingsGroupLabel = (typeof SETTINGS_SECTIONS)[number]["group"];

/**
 * The settings pages that are a grid rather than a form. They take the whole main area with a definite
 * height, as Strategy and the Companies stages do in ProjectLayout, so the rows scroll under a fixed
 * toolbar and pager instead of the page scrolling past them.
 */
const GRID_PAGES = new Set(["/settings/templates", "/settings/template-library"]);

/**
 * The settings shell: breadcrumb topbar, the section rail, and a narrower content column than the
 * workspace screens.
 *
 * <p>Account is everyone's — a portal guest has a name and a timezone like anyone else — except API keys and
 * Connected AI apps, which are staff's. The Workspace
 * group is admin-only but for Billing, which every staff member reads, and the Platform group is LightMove
 * staff's, matching the routes: hiding them is presentation, and the route guards are the gate.
 */
export function SettingsLayout() {
  const { pathname } = useLocation();
  const { user } = useAuth();

  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const isClient = isPureClient(user?.workspace?.roles ?? []);
  const isLibraryEditor = user?.platformActions.includes("TEMPLATE_LIBRARY_MANAGE") ?? false;
  const visibleGroups: SettingsGroupLabel[] = [
    "Account",
    ...(!isClient ? (["Workspace"] as const) : []),
    ...(isLibraryEditor ? (["Platform"] as const) : []),
  ];
  const isShown = (section: (typeof SETTINGS_SECTIONS)[number]) =>
    section.group === "Workspace"
      ? isAdmin || "everyStaff" in section
      : !(isClient && "staffOnly" in section);

  const groups: SidebarGroup[] = visibleGroups.map((group) => ({
    label: group,
    items: SETTINGS_SECTIONS.filter((section) => section.group === group && isShown(section)).map(
      ({ to, label, icon }) => ({ to, label, icon }),
    ),
  }));

  const section = SETTINGS_SECTIONS.find((candidate) => pathname.startsWith(candidate.to));

  return (
    <AppShell
      breadcrumb={<SettingsBreadcrumb section={section?.label ?? "Settings"} />}
      navGroups={groups}
      navBackLink={{ to: "/", label: "Back to workspace", icon: ICONS.back }}
      contentClassName={
        GRID_PAGES.has(pathname)
          ? "flex h-full w-full flex-col"
          : "mx-auto max-w-[760px] px-4 pb-[60px] pt-5 sm:px-7 sm:pt-7"
      }
    >
      <Outlet />
    </AppShell>
  );
}
