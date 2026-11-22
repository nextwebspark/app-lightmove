import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { matchPath, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { isPureClient } from "../../features/auth/roles";
import * as poolApi from "../../features/candidates/api/poolApi";
import * as clientsApi from "../../features/clients/api/clientsApi";
import { liveClients } from "../../features/clients/lib/filtering";
import * as projectsApi from "../../features/projects/api/projectsApi";
import { isActive } from "../../features/projects/lib/filtering";
import * as workspaceApi from "../../features/workspace/api/workspaceApi";
import { useWorkspaceVocabulary } from "../../features/workspace/lib/vocabulary";
import { AppShell } from "./AppShell";
import { ICONS } from "./Icon";
import { workspaceTabs } from "./mobile/mobileTabs";
import { type SidebarGroup } from "./Sidebar";
import { WorkspaceBreadcrumb } from "./Topbar";

/** Screens that draw their own toolbar and rail edge to edge and scroll inside, as Strategy does. */
const FULL_BLEED_ROUTES = ["/candidates/*", "/dashboards"];

/**
 * The app shell: topbar, the workspace sidebar with live counts, and the main panel the routed page
 * renders into.
 */
export function WorkspaceLayout() {
  return (
    <WorkspaceShell>
      <Outlet />
    </WorkspaceShell>
  );
}

/**
 * The same chrome around content that isn't a routed child — the not-found screen a bad project id
 * lands on, which cannot use the project rail because there is no project to build it from.
 *
 * The sidebar's counts ride the same queries the pages use — one cache, no extra traffic.
 */
export function WorkspaceShell({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const roles = user?.workspace?.roles ?? [];
  const clientOnly = isPureClient(roles);
  const vocabulary = useWorkspaceVocabulary();
  const { pathname, search } = useLocation();
  // Back on Positions from elsewhere in the shell, the list keeps the stage and search it was left with.
  const listSearch = pathname === "/positions" ? search : "";
  const fullBleed = FULL_BLEED_ROUTES.some((route) => matchPath(route, pathname));

  const { data: projects } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });
  const { data: clients } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
    enabled: !clientOnly,
  });
  const { data: poolSize } = useQuery({
    queryKey: poolApi.POOL_COUNT_KEY,
    queryFn: ({ signal }) => poolApi.poolCount(signal),
    enabled: !clientOnly,
  });
  const { data: members } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
    enabled: !clientOnly,
  });

  const activeCount = projects?.filter(isActive).length;

  // The list is the server's answer: every position for an admin, the caller's own for anyone else.
  const mainGroup: SidebarGroup = {
    label: "",
    items: [
      { to: "/", label: "Home", icon: ICONS.home, end: true },
      { to: `/positions${listSearch}`, label: "Positions", icon: ICONS.myProjects, count: clientOnly ? undefined : activeCount },
      ...(clientOnly ? [] : [{ to: "/dashboards", label: "Dashboards", icon: ICONS.dashboard }]),
    ],
  };

  const groups: SidebarGroup[] = clientOnly
    ? [mainGroup]
    : [
        mainGroup,
        {
          label: "Workspace",
          items: [
            { to: "/candidates", label: "Candidates", icon: ICONS.candidates, count: poolSize },
            { to: "/clients", label: vocabulary.units, icon: ICONS.clients, count: clients && liveClients(clients).length },
            { to: "/team", label: "Team", icon: ICONS.team, count: members?.length },
            { to: "/settings", label: "Settings", icon: ICONS.settings },
          ],
        },
      ];

  const sections: Record<string, string> = {
    "/positions": "Positions",
    "/dashboards": "Dashboards",
    "/candidates": "Candidates",
    "/tasks": "My tasks",
    "/clients": vocabulary.units,
    "/team": "Team",
  };

  return (
    /* Wider than the mockups' 1160px on purpose: the data tables need the room, and a wide monitor
       was otherwise leaving ~280px unused. Text blocks cap themselves. */
    <AppShell
      breadcrumb={<WorkspaceBreadcrumb section={sections[pathname] ?? null} />}
      navGroups={groups}
      mobileTabs={workspaceTabs(clientOnly, listSearch)}
      mobileTitle={user?.workspace?.name ?? "Workspace"}
      contentClassName={
        fullBleed ? "flex h-full flex-col" : "mx-auto max-w-[1440px] px-4 pb-[60px] pt-5 sm:px-7 sm:pt-7"
      }
    >
      {children}
    </AppShell>
  );
}
