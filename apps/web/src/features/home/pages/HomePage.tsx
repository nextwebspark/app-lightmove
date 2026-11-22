import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { LeaveGuard } from "../../../components/layout/LeaveGuard";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, buttonClassName, CardsSkeleton, EmptyState, useToast } from "../../../components/ui";
import { StagePill } from "../../../components/ui/Badge";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatShortDate, formatWeekdayDate, todayKey } from "../../../lib/datetime/format";
import { getDateTimePreferences } from "../../../lib/datetime/preferences";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient, seesEveryPosition } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import { liveClients } from "../../clients/lib/filtering";
import * as dashboardsApi from "../../dashboards/api/dashboardsApi";
import { ChartCard } from "../../dashboards/components/ChartCard";
import { ChartLibraryDrawer, draftFor, type ChartDraft } from "../../dashboards/components/ChartLibraryDrawer";
import { defaultTitle } from "../../dashboards/lib/chartRules";
import { useRelabel } from "../../dashboards/lib/useRelabel";
import type { ConversationScope } from "../../outreach/api/conversationApi";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import { NewProjectModal } from "../../projects/components/NewProjectModal";
import { isSeatedOn, STAGE_ORDER } from "../../projects/lib/filtering";
import * as tasksApi from "../../tasks/api/tasksApi";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as homeApi from "../api/homeApi";
import { PositionsSection, TodaySection } from "../components/HomeSections";
import { HomeGrid } from "../components/HomeGrid";
import { HomeOptions } from "../components/HomeOptions";
import { NeedsYou } from "../components/NeedsYou";
import { MAX_HOME_CHARTS, sameLayout, type ChartTile, type HomeTile } from "../lib/homeLayout";
import { useHomeEditor } from "../lib/useHomeEditor";
import { useHomeLayout } from "../lib/useHomeLayout";
import { isAgendaTask, needsYouOf } from "../lib/needsYou";

function greetingOf(firstName: string | undefined, now = new Date()): string {
  const hour = now.getHours();
  const part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  return firstName ? `${part}, ${firstName}` : part;
}

/**
 * Home, the page after sign-in. Staff see what needs them across the positions they are on, beside the sections
 * they chose; a pure client sees the positions shared with them; a new workspace sees how to set it up.
 */
export function HomePage() {
  const { user } = useAuth();
  const roles = user?.workspace?.roles ?? [];
  const clientOnly = isPureClient(roles);
  const firstName = user?.fullName?.split(/\s+/)[0];
  const { data: projects = [], isPending, isError } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });

  const header = (subtitle: string, action?: React.ReactNode) => (
    <PageHeader title={greetingOf(firstName)} subtitle={`${formatWeekdayDate(new Date())} · ${subtitle}`} action={action} />
  );

  if (isPending) {
    return (
      <>
        {header(user?.workspace?.name ?? "")}
        <CardsSkeleton cards={3} />
      </>
    );
  }
  if (isError) {
    return (
      <>
        {header(user?.workspace?.name ?? "")}
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't load Home"
          body="You may no longer have access, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </>
    );
  }
  if (clientOnly) return <ClientHome projects={projects} header={header} />;
  if (projects.length === 0 && seesEveryPosition(roles)) return <SetUpHome header={header} />;
  return <StaffHome projects={projects} header={header} />;
}

type HeaderFn = (subtitle: string, action?: React.ReactNode) => React.ReactNode;

function StaffHome({ projects, header }: { projects: Project[]; header: HeaderFn }) {
  const { user } = useAuth();
  const toast = useToast();
  const owner = { userId: user?.id ?? "", workspaceId: user?.workspace?.id ?? "" };
  const home = useHomeLayout(owner);
  const editor = useHomeEditor(home.layout);
  const { editing, layout } = editor;
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [libraryDraft, setLibraryDraft] = useState<ChartDraft | null | "library">(null);
  const [scope, setScope] = useState<ConversationScope>("mine");

  const leadsAny = projects.some((project) => isSeatedOn(project, user?.id, "LEAD"));
  const shownScope: ConversationScope = leadsAny ? scope : "mine";
  const seated = projects.filter((project) => isSeatedOn(project, user?.id));

  // Each read runs every position's own queue on the server, so it is kept a minute rather than redone on every focus.
  const outreach = useQuery({
    queryKey: homeApi.MY_OUTREACH_NEEDS_KEY(shownScope),
    queryFn: ({ signal }) => homeApi.getMyOutreachNeeds(shownScope, signal),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
  // Home's own keys, never under My tasks' `["tasks", "mine"]`: that card writes its ticks into every entry under its
  // prefix as pages of an infinite list, and a plain answer there made each tick fail.
  const today = useQuery({
    queryKey: homeApi.HOME_TASKS_KEY("today"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("today", { size: 50, signal }),
  });
  const overdue = useQuery({
    queryKey: homeApi.HOME_TASKS_KEY("overdue"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("overdue", { size: 50, signal }),
  });
  const assigned = useQuery({
    queryKey: homeApi.HOME_TASKS_KEY("assigned_by_me"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("assigned_by_me", { size: 50, signal }),
    enabled: shownScope === "team",
  });
  const library = useQuery({
    queryKey: dashboardsApi.CHART_LIBRARY_KEY(homeApi.WORKSPACE_CHARTS),
    queryFn: ({ signal }) => dashboardsApi.getChartLibrary(homeApi.WORKSPACE_CHARTS, signal),
    staleTime: 5 * 60_000,
  });
  const charts = layout.tiles.filter((tile): tile is ChartTile => tile.kind === "CHART");
  // Every chart on Home in one request, over one read of the data they share, as a dashboard asks for its charts.
  const answers = useQuery({
    queryKey: dashboardsApi.CHART_ANSWERS_KEY(homeApi.WORKSPACE_CHARTS, charts),
    queryFn: ({ signal }) => dashboardsApi.getChartAnswers(homeApi.WORKSPACE_CHARTS, charts, signal),
    enabled: charts.length > 0,
    staleTime: 60_000,
  });

  // Team reads what the caller handed out that is due by the end of today, and the whole queue of the positions they
  // lead; Mine, their own overdue and today's tasks.
  const ownTasks = useMemo(() => {
    if (shownScope !== "team") return [...(overdue.data?.tasks ?? []), ...(today.data?.tasks ?? [])];
    const { timeZone } = getDateTimePreferences();
    const endOfToday = todayKey(new Date(), timeZone);
    return (assigned.data?.tasks ?? []).filter(
      (task) => task.status === "open" && task.dueAt !== null && todayKey(new Date(task.dueAt), timeZone) <= endOfToday,
    );
  }, [shownScope, assigned.data, overdue.data, today.data]);
  const items = useMemo(
    () => needsYouOf(outreach.data?.rows ?? [], ownTasks, layout.kinds),
    [outreach.data, ownTasks, layout.kinds],
  );
  const agenda = useMemo(
    () =>
      (today.data?.tasks ?? [])
        .filter((task) => task.status === "open" && isAgendaTask(task))
        .sort((left, right) => (left.dueAt ?? "").localeCompare(right.dueAt ?? "")),
    [today.data],
  );
  const tasksPending = shownScope === "team" ? assigned.isPending : today.isPending || overdue.isPending;
  const queries = useMemo(() => new Map((library.data ?? []).map((query) => [query.id, query])), [library.data]);
  const queryOf = (id: string) => queries.get(id);
  const relabel = useRelabel();
  const count = seated.length === 1 ? "1 position you work on" : `${seated.length} positions you work on`;

  const labelOf = (tile: HomeTile) => {
    if (tile.kind !== "CHART") return SECTION_LABELS[tile.kind];
    const query = queryOf(tile.queryId);
    return tile.title || (query ? relabel(defaultTitle(query, tile.options)) : "Chart");
  };

  const handleCommit = (chart: ChartDraft) => {
    if (!editor.commitChart(chart)) toast.error(`Home can hold up to ${MAX_HOME_CHARTS} charts. Remove one to add another.`);
  };

  const chartEditor = (index: number) => {
    const tile = layout.tiles[index];
    const query = tile.kind === "CHART" ? queryOf(tile.queryId) : undefined;
    return tile.kind === "CHART" && query ? () => setLibraryDraft(draftFor(query, tile, index)) : null;
  };

  const handleSave = () => {
    if (!editor.draft) return;
    home.save.mutate(editor.draft, {
      onSuccess: () => {
        editor.stop();
        setOptionsOpen(false);
        toast.success("Home saved");
      },
      onError: (error) => toast.error(messageFor(error)),
    });
  };

  const renderTile = (tile: HomeTile) => {
    if (tile.kind === "NEEDS") {
      return (
        <NeedsYou
          items={items}
          more={outreach.data?.truncated ?? false}
          myUserId={user?.id}
          pending={outreach.isPending || tasksPending}
          refused={outreach.isError}
          scope={shownScope}
          onScopeChange={setScope}
          showScope={leadsAny}
        />
      );
    }
    if (tile.kind === "TODAY") return <TodaySection agenda={agenda} pending={today.isPending} />;
    if (tile.kind !== "CHART") return <PositionsSection positions={seated} />;
    const chartIndex = charts.indexOf(tile);
    return (
      <ChartCard
        scope={homeApi.WORKSPACE_CHARTS}
        widget={tile}
        query={library.isPending ? undefined : queryOf(tile.queryId)}
        answer={answers.data?.[chartIndex]}
        isPending={answers.isPending || library.isPending}
        isError={answers.isError || library.isError}
        className="border-u-border-strong"
      />
    );
  };

  const actions = editing ? (
    <div className="flex flex-wrap items-center gap-2">
      <Button variant="secondary" size="sm" onClick={() => setLibraryDraft("library")}>
        <Icon d={ICONS.plus} size={14} />
        Add chart
      </Button>
      <Button variant="secondary" size="sm" onClick={() => setOptionsOpen(true)}>
        <Icon d={ICONS.sliders} size={14} />
        Options
      </Button>
      <Button variant="secondary" size="sm" onClick={() => { editor.stop(); setOptionsOpen(false); }}>
        Cancel
      </Button>
      <Button size="sm" loading={home.save.isPending} onClick={handleSave}>
        Save layout
      </Button>
    </div>
  ) : (
    <Button variant="secondary" size="sm" disabled={home.pending} onClick={editor.start}>
      <Icon d={ICONS.sliders} size={14} />
      Customize
    </Button>
  );

  return (
    <>
      {header(count, actions)}

      {editing && (
        <p className="rounded-u-chip bg-u-accent-tint px-3.5 py-2.5 text-note text-u-text2">
          Drag a tile anywhere to move it, and its edge or corner to resize it. × takes a section or a chart off Home;
          Options brings a section back. Nothing changes until you save.
        </p>
      )}

      {home.pending ? (
        <CardsSkeleton cards={3} />
      ) : (
        <HomeGrid
          tiles={layout.tiles}
          editing={editing}
          labelOf={labelOf}
          renderTile={renderTile}
          onRelayout={editor.relayout}
          onEditChart={chartEditor}
          onRemove={editor.remove}
          onAddChart={() => setLibraryDraft("library")}
        />
      )}

      <LeaveGuard hasUnsavedChanges={editor.draft !== null && !sameLayout(editor.draft, home.layout)} />
      {editing && (
        <HomeOptions open={optionsOpen} onClose={() => setOptionsOpen(false)} layout={layout} onChange={editor.update} />
      )}
      {libraryDraft !== null && (
        <ChartLibraryDrawer
          scope={homeApi.WORKSPACE_CHARTS}
          library={library.data ?? []}
          onCountLabel={`${library.data?.length ?? 0} ready-made charts · ${charts.length} on Home`}
          initialDraft={libraryDraft === "library" ? null : libraryDraft}
          addLabel="Add to Home"
          onCommit={handleCommit}
          onClose={() => setLibraryDraft(null)}
        />
      )}
    </>
  );
}

const SECTION_LABELS = { NEEDS: "Needs you", TODAY: "Today", POSITIONS: "Your positions" } as const;

/** A pure client: the positions shared with them, how far each has come. No AI, nothing staff-only. */
function ClientHome({ projects, header }: { projects: Project[]; header: HeaderFn }) {
  const vocabulary = useWorkspaceVocabulary();
  const { user } = useAuth();
  const live = projects.filter((project) => !project.archivedAt);
  const sharer = vocabulary.positionSharer ?? user?.workspace?.name ?? "your search firm";
  const steps = STAGE_ORDER.filter((stage) => stage !== "LOCKED" && stage !== "CLOSED");

  return (
    <>
      {header(live.length === 1 ? `1 position shared with you by ${sharer}` : `${live.length} positions shared with you by ${sharer}`)}
      {live.length === 0 ? (
        <EmptyState
          icon={<Icon d={ICONS.briefcase} size={24} />}
          title="No positions shared with you yet"
          body={`When ${sharer} shares a position with you, it'll appear here.`}
        />
      ) : (
        <section aria-label="Your positions" className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {live.map((project) => {
            const reached = steps.indexOf(project.stage === "LOCKED" ? "UNIVERSE" : project.stage === "CLOSED" ? "DELIVERED" : project.stage);
            return (
              <Link
                key={project.id}
                to={`/projects/${project.id}`}
                className="flex flex-col gap-2.5 rounded-u-card border border-u-border-strong bg-u-surface px-4 py-3.5 hover:border-u-text3"
              >
                <span>
                  <span className="block text-body font-semibold">{project.positionTitle}</span>
                  <span className="block text-meta text-u-text3">{project.clientName}</span>
                </span>
                <span className="flex gap-1" aria-hidden="true">
                  {steps.map((stage, index) => (
                    <span key={stage} className={cn("h-1.5 flex-1 rounded-full", index <= reached ? "bg-u-accent" : "bg-u-raised")} />
                  ))}
                </span>
                <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-note text-u-text2">
                  <StagePill stage={project.stage} />
                  <span>{project.candidates > 0 ? `${project.candidates} executives` : `${project.companies} companies`}</span>
                  {project.targetDate && <span className="ml-auto text-u-text3">Due {formatShortDate(project.targetDate)}</span>}
                </span>
              </Link>
            );
          })}
        </section>
      )}
    </>
  );
}

/** A workspace with no positions yet, seen by its admin: how to set it up. */
function SetUpHome({ header }: { header: HeaderFn }) {
  const vocabulary = useWorkspaceVocabulary();
  const { user } = useAuth();
  const [modalOpen, setModalOpen] = useState(false);
  const { data: clients = [] } = useQuery({ queryKey: clientsApi.CLIENTS_KEY, queryFn: clientsApi.clients });
  const steps: { title: string; body: string; action: React.ReactNode }[] = [
    {
      title: "Open your first position",
      body: "Drop in the brief, and Uncava fills in the position for you to check.",
      action: (
        <Button size="sm" onClick={() => setModalOpen(true)}>
          New position
        </Button>
      ),
    },
    {
      title: "Connect your mailbox",
      body: "Replies and follow-ups reach Home once Uncava can see your outreach.",
      action: <Link to="/settings/integrations" className={buttonClassName("secondary", "px-3.5 py-1.5 text-note")}>Connect</Link>,
    },
    {
      title: "Invite your team",
      body: "Leads and researchers each get their own Home.",
      action: <Link to="/team" className={buttonClassName("secondary", "px-3.5 py-1.5 text-note")}>Invite</Link>,
    },
    {
      title: `Add your ${vocabulary.unitsLower}`,
      body: `Each position belongs to one ${vocabulary.unitLower}.`,
      action: <Link to="/clients" className={buttonClassName("secondary", "px-3.5 py-1.5 text-note")}>Add</Link>,
    },
  ];
  return (
    <>
      {header("a new workspace")}
      <section aria-label={`Set up ${user?.workspace?.name ?? ""}`} className="overflow-hidden rounded-u-card border border-u-border-strong bg-u-surface">
        <div className="border-b border-u-border px-4 py-3.5">
          <h2 className="text-body font-semibold">Set up {user?.workspace?.name}</h2>
          <p className="mt-0.5 text-note text-u-text3">Once a position is open, this page fills with replies and what needs you each day.</p>
        </div>
        <ol>
          {steps.map((step, index) => (
            <li key={step.title} className={cn("flex flex-wrap items-center gap-3.5 px-4 py-3.5", index > 0 && "border-t border-u-border")}>
              <span className={cn("grid size-[26px] flex-none place-items-center rounded-full border-[1.5px] font-mono text-meta font-semibold", index === 0 ? "border-u-accent bg-u-accent-tint text-u-accent" : "border-u-border-strong text-u-text3")}>
                {index + 1}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-body font-medium">{step.title}</span>
                <span className="block text-meta text-u-text3">{step.body}</span>
              </span>
              {step.action}
            </li>
          ))}
        </ol>
      </section>
      {modalOpen && <NewProjectModal open onClose={() => setModalOpen(false)} clients={liveClients(clients)} />}
    </>
  );
}
