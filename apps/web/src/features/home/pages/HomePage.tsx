import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, buttonClassName, CardsSkeleton, EmptyState } from "../../../components/ui";
import { StagePill } from "../../../components/ui/Badge";
import { cn } from "../../../lib/cn";
import { formatShortDate, formatWeekdayDate, todayKey } from "../../../lib/datetime/format";
import { getDateTimePreferences } from "../../../lib/datetime/preferences";
import { OVERVIEW_QUERY, OVERVIEW_REFRESH_MS } from "../../../lib/overviewQueries";
import { useAuth } from "../../auth/AuthProvider";
import { StartPageNotice } from "../../startpage/components/StartPageNotice";
import { isPureClient, seesEveryPosition } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import { liveClients } from "../../clients/lib/filtering";
import type { ConversationScope } from "../../outreach/api/conversationApi";
import { mailboxStateOf } from "../../outreach/lib/mailboxState";
import { useMailbox } from "../../outreach/lib/useMailbox";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import { NewProjectModal } from "../../projects/components/NewProjectModal";
import { isSeatedOn, STAGE_ORDER } from "../../projects/lib/filtering";
import * as tasksApi from "../../tasks/api/tasksApi";
import { taskKindOf } from "../../tasks/lib/taskKinds";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as homeApi from "../api/homeApi";
import { NeedsAttention, type MailboxGap } from "../components/NeedsAttention";
import { PositionsTable } from "../components/PositionsTable";
import { TodayPanel } from "../components/TodayPanel";
import { attentionOf, isAgendaTask, needsYouOf } from "../lib/needsYou";
import { todayEventsOf } from "../lib/today";

function greetingOf(firstName: string | undefined, now = new Date()): string {
  const hour = now.getHours();
  const part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  return firstName ? `${part}, ${firstName}.` : `${part}.`;
}

const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

function HomeHeader({ firstName, summary, action }: { firstName: string | undefined; summary: string; action?: React.ReactNode }) {
  const now = new Date();
  return (
    <header className="mb-6 flex flex-col gap-3 border-b border-u-border pb-5 sm:flex-row sm:items-end sm:gap-4">
      <div className="min-w-0">
        <div className="type-micro-label text-u-text3">{formatWeekdayDate(now)}</div>
        <h1 className="mt-1.5 text-figure font-semibold tracking-tight">{greetingOf(firstName, now)}</h1>
        {summary && <p className="mt-1.5 text-body text-u-text3">{summary}</p>}
      </div>
      {action && <div className="flex flex-wrap gap-2 sm:ml-auto">{action}</div>}
    </header>
  );
}

/**
 * Home, the page after sign-in. Staff see what needs them, what today holds and where their positions stand; a pure
 * client sees the positions shared with them; a new workspace sees how to set it up.
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

  const header = (summary: string, action?: React.ReactNode) => (
    <HomeHeader firstName={firstName} summary={summary} action={action} />
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

type HeaderFn = (summary: string, action?: React.ReactNode) => React.ReactNode;

function StaffHome({ projects, header }: { projects: Project[]; header: HeaderFn }) {
  const { user } = useAuth();
  const [scope, setScope] = useState<ConversationScope>("mine");
  const [newPositionOpen, setNewPositionOpen] = useState(false);
  const mailboxState = mailboxStateOf(useMailbox());
  const mailboxGap: MailboxGap =
    mailboxState.kind === "none" ? "missing" : mailboxState.kind === "error" ? "broken" : null;
  const { data: clients = [] } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
    enabled: newPositionOpen,
  });

  const leadsAny = projects.some((project) => isSeatedOn(project, user?.id, "LEAD"));
  const shownScope: ConversationScope = leadsAny ? scope : "mine";
  const active = projects.filter((project) => isSeatedOn(project, user?.id) && !project.archivedAt && project.stage !== "CLOSED");
  const clientNames = useMemo(() => new Map(projects.map((project) => [project.id, project.clientName])), [projects]);
  const clientOf = (projectId: string) => clientNames.get(projectId);

  // Home shows the cached answer at once and reads again behind it: after any write anywhere (OVERVIEW_QUERY), on
  // coming back to the tab, and every two minutes while open. No refresh button: what it shows is never older than that.
  const live = { meta: OVERVIEW_QUERY, refetchOnWindowFocus: true, refetchInterval: OVERVIEW_REFRESH_MS } as const;
  // Each read runs every position's own queue on the server, so it is kept a minute unless something changed.
  const outreach = useQuery({
    queryKey: homeApi.MY_OUTREACH_NEEDS_KEY(shownScope),
    queryFn: ({ signal }) => homeApi.getMyOutreachNeeds(shownScope, signal),
    staleTime: 60_000,
    ...live,
  });
  // Home's own keys, never under My tasks' `["tasks", "mine"]`: that card writes its ticks into every entry under its
  // prefix as pages of an infinite list, and a plain answer there made each tick fail.
  const today = useQuery({
    queryKey: homeApi.HOME_TASKS_KEY("today"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("today", { size: 50, signal }),
    ...live,
  });
  const overdue = useQuery({
    queryKey: homeApi.HOME_TASKS_KEY("overdue"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("overdue", { size: 50, signal }),
    ...live,
  });
  const assigned = useQuery({
    queryKey: homeApi.HOME_TASKS_KEY("assigned_by_me"),
    queryFn: ({ signal }) => tasksApi.getMyTasks("assigned_by_me", { size: 50, signal }),
    ...live,
    enabled: shownScope === "team",
  });
  const meetings = useQuery({
    queryKey: homeApi.MY_MEETINGS_TODAY_KEY,
    queryFn: ({ signal }) => homeApi.getMyMeetingsToday(signal),
    staleTime: 60_000,
    ...live,
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
  const { lines, comingUp } = useMemo(
    () => attentionOf(needsYouOf(outreach.data?.rows ?? [], ownTasks)),
    [outreach.data, ownTasks],
  );
  const events = useMemo(() => {
    const agenda = (today.data?.tasks ?? []).filter((task) => task.status === "open" && isAgendaTask(task));
    return todayEventsOf(meetings.data ?? [], agenda, (projectId) => clientNames.get(projectId), (task) => taskKindOf(task.kind).label);
  }, [today.data, meetings.data, clientNames]);
  const tasksPending = shownScope === "team" ? assigned.isPending : today.isPending || overdue.isPending;
  const needsPending = outreach.isPending || tasksPending;
  const todayPending = today.isPending || meetings.isPending;

  const summary = [
    needsPending
      ? null
      : lines.length === 0
        ? "Nothing needs your attention"
        : `${plural(lines.length, "action needs", "actions need")} attention`,
    todayPending ? null : events.length === 0 ? "No meetings today" : `${plural(events.length, "meeting", "meetings")} today`,
    active.length === 0 ? "No positions yet" : plural(active.length, "active position", "active positions"),
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <>
      {header(summary)}
      <StartPageNotice />
      <div className="flex flex-col gap-4">
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1.85fr)_minmax(0,1fr)]">
          <NeedsAttention
            lines={lines}
            comingUp={comingUp}
            more={outreach.data?.truncated ?? false}
            clientOf={clientOf}
            myUserId={user?.id}
            pending={needsPending}
            refused={outreach.isError}
            scope={shownScope}
            onScopeChange={setScope}
            showScope={leadsAny}
            mailboxGap={mailboxGap}
            onAnyPosition={active.length > 0}
          />
          <TodayPanel events={events} pending={todayPending} calendarRefused={meetings.isError} mailboxGap={mailboxGap} />
        </div>
        <PositionsTable positions={active} onNewPosition={() => setNewPositionOpen(true)} />
      </div>
      {newPositionOpen && <NewProjectModal open onClose={() => setNewPositionOpen(false)} clients={liveClients(clients)} />}
    </>
  );
}

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
      <StartPageNotice />
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
      {header("A new workspace, ready to set up")}
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
