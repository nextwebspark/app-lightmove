import { useEffect } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { AppShell } from "./AppShell";
import { ICONS } from "./Icon";
import { type SidebarGroup } from "./Sidebar";
import {
  rememberSettingsSection,
  settingsLandingOf,
  settingsSectionOf,
  visibleSettingsSections,
  type SettingsGroupLabel,
} from "./settingsSections";
import { SettingsBreadcrumb } from "./Topbar";

/**
 * The settings pages that are a grid rather than a form. They take the whole main area with a definite
 * height, as Strategy and the Companies stages do in ProjectLayout, so the rows scroll under a fixed
 * toolbar and pager instead of the page scrolling past them.
 */
const GRID_PAGES = new Set([
  "/settings/templates",
  "/settings/template-library",
]);

/** The settings shell: breadcrumb topbar, the section rail, and a narrower content column than the workspace screens. */
export function SettingsLayout() {
  const { pathname } = useLocation();
  const { user } = useAuth();

  useEffect(() => rememberSettingsSection(user, pathname), [user, pathname]);

  const visible = visibleSettingsSections(user);
  const groups: SidebarGroup[] = GROUP_ORDER.map((group) => ({
    label: group,
    items: visible
      .filter((section) => section.group === group)
      .map(({ to, label, icon }) => ({ to, label, icon })),
  })).filter((group) => group.items.length > 0);

  const section = settingsSectionOf(pathname);

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

const GROUP_ORDER: SettingsGroupLabel[] = [
  "Your account",
  "Workspace",
  "Platform",
];

/** `/settings` itself: the section this person last had open, or the one their role most likely came for. */
export function SettingsLanding() {
  const { user } = useAuth();
  return <Navigate to={settingsLandingOf(user)} replace />;
}
