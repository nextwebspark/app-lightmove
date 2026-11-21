import { Outlet, useLocation } from "react-router-dom";
import { AppShell } from "../../components/layout/AppShell";
import { ICONS } from "../../components/layout/Icon";
import { workspaceTabs } from "../../components/layout/mobile/mobileTabs";
import type { SidebarItem } from "../../components/layout/Sidebar";
import { useAuth } from "../auth/AuthProvider";
import { isPureClient } from "../auth/roles";
import { PlatformBreadcrumb } from "./PlatformBreadcrumb";
import { platformSectionOf, visiblePlatformSections } from "./platformSections";

/**
 * The platform console's shell: Uncava staff's own area, above every workspace and inside none. Its topbar carries
 * no workspace menu, credit chip or bell, and its rail leads back to the workspace the person is signed in to.
 */
export function PlatformLayout() {
  const { pathname } = useLocation();
  const { user } = useAuth();

  const section = platformSectionOf(pathname);
  const items = visiblePlatformSections(user).map(({ to, label, icon }): SidebarItem => ({ to, label, icon }));

  return (
    <AppShell
      breadcrumb={<PlatformBreadcrumb section={section?.label ?? null} />}
      workspaceChrome={false}
      navGroups={[{ label: "", items }]}
      navBackLink={{ to: "/", label: "Back to workspace", icon: ICONS.back }}
      mobileTabs={workspaceTabs(isPureClient(user?.workspace?.roles ?? []))}
      mobileTitle="Uncava platform"
      contentClassName={
        section?.grid && pathname === section.to
          ? "flex h-full w-full flex-col"
          : "mx-auto max-w-[760px] px-4 pb-[60px] pt-5 sm:px-7 sm:pt-7"
      }
    >
      <Outlet />
    </AppShell>
  );
}
