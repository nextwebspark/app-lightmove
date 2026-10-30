import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Outlet, useLocation, useNavigate, useParams } from "react-router-dom";
import { NotFoundPage } from "../../app/NotFoundPage";
import { useAuth } from "../../features/auth/AuthProvider";
import * as projectsApi from "../../features/projects/api/projectsApi";
import { canExecuteProjectWork } from "../../features/projects/lib/access";
import type { Project } from "../../features/projects/api/types";
import {
  ArchivePositionDialog,
  PositionActionsMenu,
  PositionArchivedNotice,
} from "../../features/projects/components/PositionArchive";
import { ProjectPeopleBar } from "../../features/projects/components/ProjectPeopleBar";
import { isArchived } from "../../features/projects/lib/filtering";
import { cn } from "../../lib/cn";
import { Spinner, useToast } from "../ui";
import { AppShell } from "./AppShell";
import { ICONS } from "./Icon";
import { positionTabs } from "./mobile/mobileTabs";
import { PositionQuickActions } from "./mobile/PositionQuickActions";
import { type SidebarGroup } from "./Sidebar";
import * as triageApi from "../../features/triage/api/triageApi";
import { stageByStatus, TRIAGE_STAGES } from "../../features/triage/lib/triageStages";
import {
  CHOSEN_VIEW_STATE,
  COMPANIES_VIEW_LABELS,
  resolveCompaniesView,
  useCompaniesViews,
} from "../../features/triage/lib/useCompaniesViews";
import { readTalentMapPreferences } from "../../features/talentmap/lib/useTalentMapPreferences";
import { ProjectBreadcrumb } from "./Topbar";
import { usePositionsOrigin } from "../../features/projects/lib/positionsOrigin";

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
 *
 * <p>Reports is here for a different reason: it paints its own ground, the page background rather
 * than the shell's surface, and a gutter around it would frame the report in a second tone. The brief
 * (the mandate's index route, matched exactly below) is full-bleed for the same reason.
 */
const FULL_BLEED_TABS = ["/companies/", "/strategy", "/reports"];

/**
 * The project workspace shell (Project.dc.html): breadcrumb topbar, the mandate sidebar — Position
 * and Strategy under "Position", the three triage stages under "Market mapping", Outreach under "CRM" for staff,
 * with Strategy's, In universe's and Outreach's views nested under them (Position.dc.html) — and the routed page.
 * The project itself is resolved from the cached list query; a deep link waits for the load and only
 * shows the not-found screen once the id is confirmed unreadable.
 */
export function ProjectLayout() {
  const { projectId } = useParams();
  const origin = usePositionsOrigin(projectId);
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const toast = useToast();
  const [confirmingArchive, setConfirmingArchive] = useState(false);
  const [quickActionsOpen, setQuickActionsOpen] = useState(false);
  // `includes`, not `endsWith`: the Companies stages are a path segment deep (/companies/universe),
  // so matching only the tail would drop all three back to the gutter-and-cap layout that leaves a
  // wide grid ending in mid-air.
  // Conversations, the sequences list and the sequence editor each own the viewport under the one Outreach header, so
  // moving between them never moves the frame.
  const currentPath = pathname.replace(/\/$/, "");
  const sequencesPath = `/projects/${projectId}/outreach/sequences`;
  const isOutreachWorkspace =
    currentPath === `/projects/${projectId}/outreach` ||
    currentPath === sequencesPath ||
    currentPath.startsWith(`${sequencesPath}/`);
  const fillsViewport = isOutreachWorkspace || VIEWPORT_FILLING_TABS.some((tab) => pathname.includes(tab));
  const isBriefTab = currentPath === `/projects/${projectId}`;
  const fullBleed = isBriefTab || isOutreachWorkspace || FULL_BLEED_TABS.some((tab) => pathname.includes(tab));

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

  const { user } = useAuth();
  const isStaff = project ? canExecuteProjectWork(project, user?.id, user?.workspace?.roles) : false;
  const { views: universeViews } = useCompaniesViews("inUniverse", isStaff);

  if (!project) {
    if (isPending) {
      return (
        <div className="flex min-h-dvh items-center justify-center">
          <Spinner />
        </div>
      );
    }
    // Not a redirect to the list: a deleted mandate and one the caller is not seated on both land
    // here, and neither is a navigation the user asked for.
    return (
      <NotFoundPage
        title="We couldn't open that position"
        body="It may have been deleted, or you may not be on its team. Ask the mandate's lead if you think you should have access."
      />
    );
  }

  const base = `/projects/${project.id}`;
  const query = new URLSearchParams(search);
  const universePath = `${base}/companies/${stageByStatus("inUniverse").slug}`;
  const universeView =
    currentPath === universePath
      ? resolveCompaniesView(universeViews, query.get("view"), readTalentMapPreferences(project.id).view)
      : null;
  const peopleMode = currentPath === `${base}/strategy` && query.get("mode") === "people";
  const groups: SidebarGroup[] = [
    {
      label: "Position",
      items: [
        { to: base, label: "Brief", icon: ICONS.position, end: true },
        {
          to: `${base}/strategy`,
          label: "Strategy",
          icon: ICONS.strategy,
          // People mode is the position's WORK_EXECUTE, as StrategyPage decides it.
          children: isStaff
            ? [
                {
                  key: "companies",
                  to: `${base}/strategy`,
                  label: "Companies",
                  icon: ICONS.building,
                  active: currentPath === `${base}/strategy` && !peopleMode,
                },
                {
                  key: "people",
                  to: `${base}/strategy?mode=people`,
                  label: "People",
                  icon: ICONS.candidates,
                  active: peopleMode,
                },
              ]
            : undefined,
        },
      ],
    },
    {
      label: "Market mapping",
      // The three triage stages, each its own page: a consultant works one at a time and sends a
      // colleague the shortlist, not "the Companies screen, then the second tab".
      items: TRIAGE_STAGES.map((stage) => ({
        to: `${base}/companies/${stage.slug}`,
        label: stage.label,
        icon: stage.icon,
        count: triageCounts?.[stage.status],
        children:
          stage.status === "inUniverse" && universeViews.length > 1
            ? universeViews.map((view) => ({
                key: view,
                to: `${universePath}?view=${view}`,
                state: CHOSEN_VIEW_STATE,
                label: COMPANIES_VIEW_LABELS[view].label,
                icon: COMPANIES_VIEW_LABELS[view].icon,
                active: universeView === view,
              }))
            : undefined,
      })),
    },
    {
      label: "CRM",
      items: [
        ...(isStaff
          ? [
              {
                to: `${base}/outreach`,
                label: "Outreach",
                icon: ICONS.outreach,
                children: [
                  {
                    key: "conversations",
                    to: `${base}/outreach`,
                    label: "Conversations",
                    icon: ICONS.mail,
                    active: currentPath === `${base}/outreach`,
                  },
                  {
                    key: "sequences",
                    to: sequencesPath,
                    label: "Sequences",
                    icon: ICONS.listOrdered,
                    active: currentPath === sequencesPath || currentPath.startsWith(`${sequencesPath}/`),
                  },
                ],
              },
            ]
          : []),
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
      mobileTabs={positionTabs(project.id, isStaff)}
      mobileTitle={project.positionTitle}
      onQuickAction={isStaff && !isArchived(project) ? () => setQuickActionsOpen(true) : undefined}
      breadcrumb={
        <ProjectBreadcrumb
          clientName={project.clientName}
          positionTitle={project.positionTitle}
          // The crumb says Positions, so it leads to a list of them, never the business units it might have come from.
          positionsHref={origin.path.startsWith("/clients") ? "/" : origin.path}
        />
      }
      topbarActions={
        <div className="flex min-w-0 items-center gap-1">
          {/* A phone gives the position's title the room; the team is under More → Team & access. */}
          <span className="hidden sm:contents">
            <ProjectPeopleBar project={project} />
          </span>
          <PositionActionsMenu project={project} onArchive={() => setConfirmingArchive(true)} />
        </div>
      }
      notice={isArchived(project) ? <PositionArchivedNotice project={project} /> : undefined}
      navGroups={groups.filter((group) => group.items.length > 0)}
      navBackLink={{ to: origin.path, label: origin.label, title: `Back to ${origin.label}`, icon: ICONS.back }}
      assistantContext={`${project.clientName} · ${project.positionTitle}`}
      assistantProjectId={project.id}
      /* Wider than the mockups' 1160px on purpose — see WorkspaceLayout for the reasoning. */
      contentClassName={cn(
        fullBleed ? "flex min-h-full w-full flex-col" : "mx-auto max-w-[1440px] px-4 pb-[60px] pt-5 sm:px-7 sm:pt-7",
        fillsViewport && "flex h-full flex-col",
      )}
    >
      <Outlet context={{ project } satisfies ProjectOutletContext} />
      {isStaff && (
        <PositionQuickActions
          projectId={project.id}
          open={quickActionsOpen}
          onClose={() => setQuickActionsOpen(false)}
        />
      )}
      {confirmingArchive && (
        <ArchivePositionDialog
          project={project}
          onClose={() => setConfirmingArchive(false)}
          onArchived={() => {
            setConfirmingArchive(false);
            toast.success(`${project.positionTitle} archived`);
            navigate(origin.path.startsWith("/clients") ? "/" : origin.path);
          }}
        />
      )}
    </AppShell>
  );
}

/** What the shell hands the routed pages — the resolved project, for heroes and headers. */
export interface ProjectOutletContext {
  project: Project;
}
