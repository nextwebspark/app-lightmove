import { useQuery } from "@tanstack/react-query";
import { Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import * as workspaceApi from "../../features/workspace/api/workspaceApi";
import { ICONS } from "./Icon";
import { Sidebar, type SidebarGroup } from "./Sidebar";
import { SettingsBreadcrumb, Topbar } from "./Topbar";

/**
 * The settings shell: breadcrumb topbar, a back-link sidebar carrying only the sections that exist
 * (General, Members), and a narrower content column than the workspace screens.
 */
export function SettingsLayout() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const verified = user?.emailVerified ?? false;
  const isAdmin = user?.workspace?.role === "ADMIN";

  const { data: pending } = useQuery({
    queryKey: workspaceApi.PENDING_MEMBERS_KEY,
    queryFn: workspaceApi.pendingMembers,
    enabled: isAdmin && verified,
  });

  const groups: SidebarGroup[] = [
    {
      label: "Workspace",
      items: [
        { to: "/settings/general", label: "General", icon: ICONS.settings },
        { to: "/settings/members", label: "Members", icon: ICONS.members, count: pending?.length || undefined },
      ],
    },
  ];

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <Topbar
        breadcrumb={<SettingsBreadcrumb section={pathname.endsWith("members") ? "Members" : "General"} />}
      />

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
