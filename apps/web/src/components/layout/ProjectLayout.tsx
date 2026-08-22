import { useQuery } from "@tanstack/react-query";
import { Navigate, Outlet, useLocation, useParams } from "react-router-dom";
import * as projectsApi from "../../features/projects/api/projectsApi";
import type { Project } from "../../features/projects/api/types";
import { cn } from "../../lib/cn";
import { Spinner, StagePill } from "../ui";
import { ICONS } from "./Icon";
import { Sidebar, type SidebarGroup } from "./Sidebar";
import { ProjectBreadcrumb, Topbar } from "./Topbar";

/**
 * Tabs whose own content scrolls, so the shell must not. They need a *definite* height to size that
 * region against: `min-height` leaves the wrapper's height auto, an auto-height flex column grows to
 * its content, and the table pushes the page into scrolling instead. `h-full` is opt-in rather than
 * shared because it caps the wrapper — a tab that scrolls normally would have its overflow clipped.
 */
const VIEWPORT_FILLING_TABS = ["/triage", "/strategy"];

/**
 * Tabs that own the whole main area: no gutter, and no 1440px cap.
 *
 * <p>The cap is right for a reading column and wrong for a workspace. Strategy is a filter rail and a
 * ten-column table side by side, and on a wide screen the cap left the table ending in mid-air with
 * the space it needed sitting empty beside it — the columns that got squeezed were the ones carrying
 * the data.
 */
const FULL_BLEED_TABS = ["/strategy"];

/**
 * The project workspace shell (Project.dc.html): breadcrumb topbar, the mandate sidebar — Position
 * and Strategy under "Mandate", Triage under "Companies", the people tabs — and the routed page.
 * The project itself is resolved from the cached list query; a deep link waits for the load and only
 * redirects once the id is confirmed absent.
 */
export function ProjectLayout() {
  const { projectId } = useParams();
  const { pathname } = useLocation();
  const fillsViewport = VIEWPORT_FILLING_TABS.some((tab) => pathname.endsWith(tab));
  const fullBleed = FULL_BLEED_TABS.some((tab) => pathname.endsWith(tab));

  const { data: projects, isPending } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
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
      items: [{ to: `${base}/triage`, label: "Triage", icon: ICONS.triage }],
    },
    {
      label: "People",
      items: [
        { to: `${base}/candidates`, label: "Candidates", icon: ICONS.candidates },
        { to: `${base}/outreach`, label: "Outreach", icon: ICONS.outreach },
      ],
    },
    {
      label: "Project",
      items: [
        { to: `${base}/reports`, label: "Reports", icon: ICONS.reports },
        { to: `${base}/team`, label: "Team & access", icon: ICONS.team },
      ],
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
          {/* Wider than the mockups' 1160px on purpose — see WorkspaceLayout for the reasoning. */}
          <div
            className={cn(
              fullBleed ? "w-full" : "mx-auto max-w-[1440px] px-7 pb-[60px] pt-7",
              fillsViewport && "flex h-full flex-col",
            )}
          >
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
