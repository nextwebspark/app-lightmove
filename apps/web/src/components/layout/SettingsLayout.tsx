import { Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { ICONS } from "./Icon";
import { Sidebar, type SidebarGroup } from "./Sidebar";
import { SettingsBreadcrumb, Topbar } from "./Topbar";

/**
 * The settings sections that exist, in the mockup's two groups.
 *
 * One table drives both the sidebar and the breadcrumb, so a section cannot appear in the rail under
 * one name and in the header under another — which is what the two-way ternary this replaced allowed.
 * The mockup's Security, Notifications, Billing and Integrations are absent until their screens are
 * built: an item that leads nowhere is worse than no item.
 */
const SETTINGS_SECTIONS = [
  { to: "/settings/profile", label: "Profile", icon: ICONS.profile, group: "Account" },
  { to: "/settings/general", label: "General", icon: ICONS.settings, group: "Workspace" },
  { to: "/settings/members", label: "Members", icon: ICONS.members, group: "Workspace" },
] as const;

type SettingsGroupLabel = (typeof SETTINGS_SECTIONS)[number]["group"];

/**
 * The settings shell: breadcrumb topbar, the section rail, and a narrower content column than the
 * workspace screens.
 *
 * <p>Account is everyone's — a portal guest has a name and a timezone like anyone else. The Workspace
 * group is admin-only, matching the routes: hiding it is presentation, and `RequireAdmin` is the guard.
 */
export function SettingsLayout() {
  const { pathname } = useLocation();
  const { user } = useAuth();

  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const visibleGroups: SettingsGroupLabel[] = isAdmin ? ["Account", "Workspace"] : ["Account"];

  const groups: SidebarGroup[] = visibleGroups.map((group) => ({
    label: group,
    items: SETTINGS_SECTIONS.filter((section) => section.group === group).map(
      ({ to, label, icon }) => ({ to, label, icon }),
    ),
  }));

  const section = SETTINGS_SECTIONS.find((candidate) => pathname.startsWith(candidate.to));

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <Topbar breadcrumb={<SettingsBreadcrumb section={section?.label ?? "Settings"} />} />

      <div className="flex min-h-0 flex-1 gap-3.5 px-3.5 pb-3.5">
        <Sidebar
          groups={groups}
          backLink={{ to: "/", label: "Back to workspace", icon: ICONS.back }}
        />

        <main className="min-w-0 flex-1 overflow-y-auto rounded-[10px] border border-line bg-panel">
          <div className="mx-auto max-w-[760px] px-7 pb-[60px] pt-7">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
