import { useQuery } from "@tanstack/react-query";
import { Outlet } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { isPureClient } from "../../features/auth/roles";
import * as clientsApi from "../../features/clients/api/clientsApi";
import * as projectsApi from "../../features/projects/api/projectsApi";
import * as workspaceApi from "../../features/workspace/api/workspaceApi";
import { ICONS } from "./Icon";
import { Sidebar, type SidebarGroup } from "./Sidebar";
import { Topbar } from "./Topbar";

/**
 * The app shell: topbar, the workspace sidebar with live counts, and the main panel the routed page
 * renders into. The sidebar's counts ride the same queries the pages use — one cache, no extra
 * traffic.
 */
export function WorkspaceLayout() {
  const { user } = useAuth();
  const roles = user?.workspace?.roles ?? [];
  const clientOnly = isPureClient(roles);

  const { data: projects } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });
  const { data: clients } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
    enabled: !clientOnly,
  });
  const { data: members } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
    enabled: !clientOnly,
  });

  const myMemberId = members?.find((m) => m.userId === user?.id)?.memberId;
  const active = projects?.filter((p) => p.stage !== "DELIVERED" && p.stage !== "CLOSED");
  const myCount = myMemberId
    ? active?.filter((p) => p.team.some((seat) => seat.memberId === myMemberId)).length
    : undefined;

  const projectsGroup: SidebarGroup = clientOnly
    ? {
        label: "Projects",
        items: [{ to: "/", label: "My projects", icon: ICONS.myProjects, end: true }],
      }
    : {
        label: "Projects",
        items: [
          { to: "/", label: "My projects", icon: ICONS.myProjects, count: myCount, end: true },
          { to: "/all", label: "All projects", icon: ICONS.allProjects, count: projects?.length },
        ],
      };

  const groups: SidebarGroup[] = clientOnly
    ? [projectsGroup]
    : [
        projectsGroup,
        {
          label: "Workspace",
          items: [
            { to: "/clients", label: "Clients", icon: ICONS.clients, count: clients?.length },
            { to: "/team", label: "Team", icon: ICONS.team, count: members?.length },
            // Every staff member's, not just an admin's: the rail lands on the section everyone can
            // read (Profile), and the shell hides the workspace sections from a non-admin. An admin
            // reaching for workspace settings has the topbar dropdown's direct link.
            { to: "/settings/profile", label: "Settings", icon: ICONS.settings },
          ],
        },
      ];

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <Topbar />

      <div className="flex min-h-0 flex-1 gap-3.5 px-3.5 pb-3.5">
        <Sidebar groups={groups} />

        <main className="min-w-0 flex-1 overflow-y-auto rounded-[10px] border border-line bg-panel">
          {/* Wider than the mockups' 1160px on purpose: the data tables need the room, and a wide
              monitor was otherwise leaving ~280px unused. Text blocks cap themselves. */}
          <div className="mx-auto max-w-[1440px] px-7 pb-[60px] pt-7">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
