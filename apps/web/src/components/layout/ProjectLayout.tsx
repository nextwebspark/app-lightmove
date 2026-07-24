import { useQuery } from "@tanstack/react-query";
import { Navigate, Outlet, useParams } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import * as projectsApi from "../../features/projects/api/projectsApi";
import type { Project } from "../../features/projects/api/types";
import { Spinner, StagePill } from "../ui";
import { ICONS } from "./Icon";
import { Sidebar, type SidebarGroup } from "./Sidebar";
import { ProjectBreadcrumb, Topbar } from "./Topbar";

/**
 * The project workspace shell (Project.dc.html): breadcrumb topbar, the mandate sidebar — Position
 * and Strategy under "Mandate", Sourcing under "Companies", the people tabs — and the routed page.
 * The project itself is resolved from the cached list query; a deep link waits for the load and only
 * redirects once the id is confirmed absent.
 */
export function ProjectLayout() {
  const { projectId } = useParams();
  const { user } = useAuth();
  const verified = user?.emailVerified ?? false;

  const { data: projects, isPending } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
    enabled: verified,
  });
  const project = projects?.find((p) => p.id === projectId);

  if (!project) {
    if (isPending) {
      return (
        <div className="flex min-h-screen items-center justify-center">
          <Spinner />
        </div>
      );
    }
    return <Navigate to="/" replace />;
  }

  const base = `/projects/${project.id}`;
  const groups: SidebarGroup[] = [
    {
      label: "Mandate",
      items: [
        { to: base, label: "Position", icon: ICONS.position, end: true },
        { to: `${base}/strategy`, label: "Strategy", icon: ICONS.strategy },
      ],
    },
    {
      label: "Companies",
      items: [{ to: `${base}/sourcing`, label: "Sourcing", icon: ICONS.sourcing }],
    },
    {
      label: "People",
      items: [
        { to: `${base}/candidates`, label: "Candidates", icon: ICONS.candidates },
        { to: `${base}/outreach`, label: "Outreach", icon: ICONS.outreach },
        { to: `${base}/reports`, label: "Reports", icon: ICONS.reports },
      ],
    },
    {
      label: "Project",
      items: [{ to: `${base}/team`, label: "Team & access", icon: ICONS.team }],
    },
  ];

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <Topbar
        breadcrumb={
          <ProjectBreadcrumb clientName={project.clientName} positionTitle={project.positionTitle} />
        }
      />

      <div className="flex min-h-0 flex-1 gap-3.5 px-3.5 pb-3.5">
        <Sidebar
          groups={groups}
          backLink={{ to: "/", label: "All projects", icon: ICONS.back }}
          header={<StagePill stage={project.stage} />}
        />

        <main className="min-w-0 flex-1 overflow-y-auto rounded-[10px] border border-line bg-panel">
          <div className="mx-auto max-w-[1160px] px-7 pb-[60px] pt-7">
            <Outlet context={{ project } satisfies ProjectOutletContext} />
          </div>
        </main>
      </div>
    </div>
  );
}

/** What the shell hands the routed pages — the resolved project, for heroes and headers. */
export interface ProjectOutletContext {
  project: Project;
}
