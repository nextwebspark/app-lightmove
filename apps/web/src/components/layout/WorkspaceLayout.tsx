import { useQuery } from "@tanstack/react-query";
import { Outlet } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import * as clientsApi from "../../features/clients/api/clientsApi";
import * as projectsApi from "../../features/projects/api/projectsApi";
import * as workspaceApi from "../../features/workspace/api/workspaceApi";
import { ICONS } from "./Icon";
import { Sidebar, type SidebarGroup } from "./Sidebar";
import { Topbar } from "./Topbar";
import { UnverifiedBanner } from "./UnverifiedBanner";

/**
 * The app shell: topbar, the workspace sidebar with live counts, and the main panel the routed page
 * renders into. The sidebar's counts ride the same queries the pages use — one cache, no extra
 * traffic.
 */
export function WorkspaceLayout() {
  const { user } = useAuth();
  const verified = user?.emailVerified ?? false;
  const roles = user?.workspace?.roles ?? [];
  const isAdmin = roles.includes("ADMIN");
  // A pure client (only the CLIENT role) is read-only and scoped to the mandates they're attached to.
  // The registry and roster are staff surfaces they can't read — don't query them, don't show them.
  const clientOnly = roles.includes("CLIENT") && !roles.some((role) => role === "ADMIN" || role === "MEMBER");

  const { data: projects } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
    enabled: verified,
  });
  const { data: clients } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
    enabled: verified && !clientOnly,
  });
  const { data: members } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
    enabled: verified && !clientOnly,
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
            ...(isAdmin
              ? [{ to: "/settings/general", label: "Settings", icon: ICONS.settings }]
              : []),
          ],
        },
      ];

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      {user && !verified && <UnverifiedBanner email={user.email} />}
      <Topbar />

      <div className="flex min-h-0 flex-1 gap-3.5 px-3.5 pb-3.5">
        <Sidebar groups={groups} />

        <main className="min-w-0 flex-1 overflow-y-auto rounded-[10px] border border-line bg-panel">
          <div className="mx-auto max-w-[1160px] px-7 pb-[60px] pt-7">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
