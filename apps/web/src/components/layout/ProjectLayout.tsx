import { useQuery } from "@tanstack/react-query";
import { Navigate, Outlet, useLocation, useParams } from "react-router-dom";
import * as projectsApi from "../../features/projects/api/projectsApi";
import type { Project } from "../../features/projects/api/types";
import { cn } from "../../lib/cn";
import { Spinner, StagePill } from "../ui";
import { AppShell } from "./AppShell";
import { ICONS } from "./Icon";
import { type SidebarGroup } from "./Sidebar";
import * as triageApi from "../../features/triage/api/triageApi";
import { TRIAGE_STAGES } from "../../features/triage/lib/triageStages";
import { ProjectBreadcrumb } from "./Topbar";

/**
 * Tabs whose own content scrolls, so the shell must not. They need a *definite* height to size that
 * region against: `min-height` leaves the wrapper's height auto, an auto-height flex column grows to
 * its content, and the table pushes the page into scrolling instead. `h-full` is opt-in rather than
 * shared because it caps the wrapper — a tab that scrolls normally would have its overflow clipped.
 */
const VIEWPORT_FILLING_TABS = ["/companies/", "/strategy"];

/**
 * Tabs that own the whole main area: no gutter, and no 1440px cap.
 *
 * <p>The cap is right for a reading column and wrong for a workspace. Strategy is a filter rail and a
 * ten-column table side by side, and on a wide screen the cap left the table ending in mid-air with
 * the space it needed sitting empty beside it — the columns that got squeezed were the ones carrying
 * the data.
 */
const FULL_BLEED_TABS = ["/companies/", "/strategy"];

/**
 * The project workspace shell (Project.dc.html): breadcrumb topbar, the mandate sidebar — Position
 * and Strategy under "Mandate", the three triage stages under "Companies", the people tabs — and the
 * routed page.
 * The project itself is resolved from the cached list query; a deep link waits for the load and only
 * redirects once the id is confirmed absent.
 */
export function ProjectLayout() {
  const { projectId } = useParams();
  const { pathname } = useLocation();
  // `includes`, not `endsWith`: the Companies stages are a path segment deep (/companies/universe),
  // so matching only the tail would drop all three back to the gutter-and-cap layout that leaves a
  // wide grid ending in mid-air.
  const fillsViewport = VIEWPORT_FILLING_TABS.some((tab) => pathname.includes(tab));
  const fullBleed = FULL_BLEED_TABS.some((tab) => pathname.includes(tab));

  const { data: projects, isPending } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });
  const project = projects?.find((p) => p.id === projectId);

  // Undefined on a refused or still-loading read, which is what the rail wants: no badge at all
  // rather than a zero nobody has read.
  const { data: triageCounts } = useQuery({
    queryKey: triageApi.TRIAGE_COUNTS_KEY(projectId ?? ""),
    queryFn: ({ signal }) => triageApi.getTriageCounts(projectId!, signal),
    enabled: Boolean(projectId),
  });

  if (!project) {
    if (isPending) {
      return (
        <div className="flex min-h-dvh items-center justify-center">
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
      // The three triage stages, each its own page: a consultant works one at a time and sends a
      // colleague the shortlist, not "the Companies screen, then the second tab".
      items: TRIAGE_STAGES.map((stage) => ({
        to: `${base}/companies/${stage.slug}`,
        label: stage.label,
        icon: stage.icon,
        count: triageCounts?.[stage.status],
      })),
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
    <AppShell
      breadcrumb={
        <ProjectBreadcrumb clientName={project.clientName} positionTitle={project.positionTitle} />
      }
      navGroups={groups}
      navBackLink={{ to: "/", label: "All projects", icon: ICONS.back }}
      navHeader={<StagePill stage={project.stage} />}
      /* Wider than the mockups' 1160px on purpose — see WorkspaceLayout for the reasoning. */
      contentClassName={cn(
        fullBleed ? "w-full" : "mx-auto max-w-[1440px] px-4 pb-[60px] pt-5 sm:px-7 sm:pt-7",
        fillsViewport && "flex h-full flex-col",
      )}
    >
      <Outlet context={{ project } satisfies ProjectOutletContext} />
    </AppShell>
  );
}

/** What the shell hands the routed pages — the resolved project, for heroes and headers. */
export interface ProjectOutletContext {
  project: Project;
}
